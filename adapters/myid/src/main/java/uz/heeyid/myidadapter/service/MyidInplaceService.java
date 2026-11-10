package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.client.MyidJson.imageIssue;
import static uz.heeyid.myidadapter.client.MyidJson.isPng;
import static uz.heeyid.myidadapter.client.MyidJson.parse;
import static uz.heeyid.myidadapter.client.MyidJson.reasonKey;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.invalid;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.text;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Grant;
import uz.heeyid.myidadapter.client.MyidJson;
import uz.heeyid.myidadapter.model.request.MyidInplaceJobRequest;
import uz.heeyid.myidadapter.model.request.MyidInplaceSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CreateInplaceJobRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceJobResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceJobResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceMedia;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceMediaResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenInplaceSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.Reuid;
import uz.khakimjanovich.contracts.myidadapter.api.model.Session;
import uz.khakimjanovich.contracts.myidadapter.api.model.UploadInplaceMediaRequest;

/** Inplace, new flow (#/ru/myid_inplace_new_flow). */
@Service
@RequiredArgsConstructor
public class MyidInplaceService {

  private final MyidClient myid;
  private final MyidJson json;

  public OpenSessionResult openInplaceSession(String tenant, OpenInplaceSessionRequest request) {
    var documents = (request.getPassData() == null ? 0 : 1) + (request.getPinfl() == null ? 0 : 1);
    var byDocument = documents == 1 && request.getBirthDate() != null && request.getReuid() == null;
    var byReuid = request.getReuid() != null && documents == 0 && request.getBirthDate() == null;
    if (!byDocument && !byReuid) {
      throw invalid(
          "passData", "give one of: passData with birthDate, pinfl with birthDate, or reuid alone");
    }
    var session =
        MyidInplaceSessionRequest.builder()
            .passData(request.getPassData())
            .pinfl(request.getPinfl())
            .birthDate(text(request.getBirthDate()))
            .isResident(request.getIsResident())
            .reuid(text(request.getReuid()))
            .build();
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            c ->
                c.post()
                    .uri("/api/v1/inplace/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(session));
    return new OpenSessionResult(
        answer.requestId(), SUCCESS, parse(answer, b -> json.read(b, Session.class)));
  }

  public InplaceMediaResult uploadInplaceMedia(String tenant, UploadInplaceMediaRequest request) {
    myid.tenant(tenant);
    var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("session_id", request.getSessionId().toString());
    parts.add("photo_front", file(request.getPhotoFront(), "photo_front"));
    if (request.getPhotoFocus() != null) {
      parts.add("photo_focus", file(request.getPhotoFocus(), "photo_focus"));
    }
    var answer =
        myid.media(
            c ->
                c.post()
                    .uri("/api/v1/inplace/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts));
    return new InplaceMediaResult(
        answer.requestId(), SUCCESS, parse(answer, b -> json.read(b, InplaceMedia.class)));
  }

  public JobResult createInplaceJob(String tenant, CreateInplaceJobRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            c ->
                c.post()
                    .uri("/api/v1/inplace/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        new MyidInplaceJobRequest(
                            request.getSessionId().toString(), request.getMediaId().toString())));
    return json.job(answer);
  }

  /** Empty while MyID still processes the job (its 202). */
  public Optional<InplaceJobResultResult> getInplaceJobResult(String tenant, UUID jobId) {
    var answer =
        myid.withTokenLong(
            tenant, Grant.CLIENT, c -> c.get().uri("/api/v1/inplace/jobs/{id}", jobId));
    if (answer.status() == 202) {
      return Optional.empty();
    }
    var result =
        parse(
            answer,
            b -> {
              var data = json.read(b.required("data"), InplaceJobResult.class);
              var reuid = b.path("reuid");
              if (data.getReuid() == null && reuid.isObject()) {
                data.setReuid(json.read(reuid, Reuid.class));
              }
              return data.reasonKey(reasonKey(data.getResultCode()))
                  .imageIssue(imageIssue(data.getResultCode()));
            });
    return Optional.of(new InplaceJobResultResult(answer.requestId(), SUCCESS, result));
  }

  private static ByteArrayResource file(byte[] photo, String name) {
    return new ByteArrayResource(photo) {
      @Override
      public String getFilename() {
        return name + (isPng(photo) ? ".png" : ".jpg");
      }
    };
  }
}
