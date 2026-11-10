package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.client.MyidJson.dataUri;
import static uz.heeyid.myidadapter.client.MyidJson.imageIssue;
import static uz.heeyid.myidadapter.client.MyidJson.parse;
import static uz.heeyid.myidadapter.client.MyidJson.reasonKey;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.invalid;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Grant;
import uz.heeyid.myidadapter.client.MyidJson;
import uz.heeyid.myidadapter.model.request.MyidCompareByExternalIdRequest;
import uz.heeyid.myidadapter.model.request.MyidCompareTaskRequest;
import uz.heeyid.myidadapter.model.request.MyidCompareTaskRequest.PhotoFromCamera;
import uz.heeyid.myidadapter.model.request.MyidJobIdRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultByExternalIdRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.CreateCompareTaskRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobResult;

/** Backend API without personal data (#/ru/compare). */
@Service
@RequiredArgsConstructor
public class MyidCompareService {

  private final MyidClient myid;
  private final MyidJson json;

  public JobResult createCompareTask(String tenant, CreateCompareTaskRequest request) {
    var comparison = Boolean.TRUE.equals(request.getComparison());
    if (!comparison && !Boolean.TRUE.equals(request.getLiveness())) {
      throw invalid("liveness", "liveness or comparison must be true");
    }
    if (comparison && request.getPhotoFromPassport() == null) {
      throw invalid("photoFromPassport", "required when comparison is true");
    }
    var clientId = myid.tenant(tenant).clientId();
    var task =
        MyidCompareTaskRequest.builder()
            .photoFromCamera(new PhotoFromCamera(dataUri(request.getPhotoFront())))
            .photoFromPassport(
                request.getPhotoFromPassport() == null
                    ? null
                    : dataUri(request.getPhotoFromPassport()))
            .agreedOnTerms(request.getAgreedOnTerms())
            .clientId(clientId)
            .liveness(request.getLiveness())
            .comparison(request.getComparison())
            .externalId(request.getExternalId())
            .threshold(request.getThreshold())
            .pinfl(request.getPinfl())
            .passData(request.getPassData())
            .phone(request.getPhone())
            .build();
    var answer =
        myid.withToken(
            tenant,
            Grant.PASSWORD,
            c ->
                c.post()
                    .uri("/api/v1/authentication/compare-photos-task")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(task));
    return json.job(answer);
  }

  /** Empty while MyID still processes the task (its 202). */
  public Optional<CompareResultResult> getCompareResult(
      String tenant, CompareResultRequest request) {
    return compareResult(
        tenant,
        "/api/v1/authentication/compare-photos-status",
        new MyidJobIdRequest(request.getJobId()));
  }

  /** Empty while MyID still processes the task (its 202). */
  public Optional<CompareResultResult> getCompareResultByExternalId(
      String tenant, CompareResultByExternalIdRequest request) {
    return compareResult(
        tenant,
        "/api/v1/authentication/compare-photos-status-by-external",
        new MyidCompareByExternalIdRequest(
            request.getExternalId(), myid.tenant(tenant).clientId()));
  }

  private Optional<CompareResultResult> compareResult(String tenant, String path, Object query) {
    var answer =
        myid.withTokenLong(
            tenant,
            Grant.PASSWORD,
            c -> c.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(query));
    if (answer.status() == 202) {
      return Optional.empty();
    }
    var result =
        parse(
            answer,
            b -> {
              var read = json.read(b, CompareResult.class);
              return read.reasonKey(reasonKey(read.getResultCode()))
                  .imageIssue(imageIssue(read.getResultCode()));
            });
    return Optional.of(new CompareResultResult(answer.requestId(), SUCCESS, result));
  }
}
