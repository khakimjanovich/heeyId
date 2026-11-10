package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.utils.MyidPhotoUtils.dataUri;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.invalid;

import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Grant;
import uz.heeyid.myidadapter.client.MyidCompareHttpClient;
import uz.heeyid.myidadapter.client.MyidLongHttpClient;
import uz.heeyid.myidadapter.mapper.MyidCompareMapper;
import uz.heeyid.myidadapter.model.request.MyidCompareByExternalIdRequest;
import uz.heeyid.myidadapter.model.request.MyidCompareTaskRequest;
import uz.heeyid.myidadapter.model.request.MyidCompareTaskRequest.PhotoFromCamera;
import uz.heeyid.myidadapter.model.request.MyidJobIdRequest;
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
  private final MyidCompareHttpClient compareHttp;
  private final MyidLongHttpClient longHttp;
  private final MyidCompareMapper mapper;

  public JobResult createCompareTask(CreateCompareTaskRequest request) {
    var comparison = Boolean.TRUE.equals(request.getComparison());
    if (!comparison && !Boolean.TRUE.equals(request.getLiveness())) {
      throw invalid("liveness", "liveness or comparison must be true");
    }
    if (comparison && request.getPhotoFromPassport() == null) {
      throw invalid("photoFromPassport", "required when comparison is true");
    }
    var clientId = myid.credential().clientId();
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
    var answer = myid.withToken(Grant.PASSWORD, auth -> compareHttp.createTask(auth, task));
    return mapper.job(answer);
  }

  /** Empty while MyID still processes the task (its 202). */
  public Optional<CompareResultResult> getCompareResult(CompareResultRequest request) {
    var query = new MyidJobIdRequest(request.getJobId());
    return compareResult(auth -> longHttp.compareResult(auth, query));
  }

  /** Empty while MyID still processes the task (its 202). */
  public Optional<CompareResultResult> getCompareResultByExternalId(
      CompareResultByExternalIdRequest request) {
    var query =
        new MyidCompareByExternalIdRequest(request.getExternalId(), myid.credential().clientId());
    return compareResult(auth -> longHttp.compareResultByExternalId(auth, query));
  }

  private Optional<CompareResultResult> compareResult(
      Function<String, ResponseEntity<byte[]>> call) {
    var answer = myid.withToken(Grant.PASSWORD, call);
    return mapper.result(answer);
  }
}
