package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.utils.MyidPhotoUtils.isPng;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.invalid;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.text;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Grant;
import uz.heeyid.myidadapter.client.MyidInplaceHttpClient;
import uz.heeyid.myidadapter.client.MyidLongHttpClient;
import uz.heeyid.myidadapter.client.MyidMediaHttpClient;
import uz.heeyid.myidadapter.mapper.MyidInplaceMapper;
import uz.heeyid.myidadapter.model.request.MyidInplaceJobRequest;
import uz.heeyid.myidadapter.model.request.MyidInplaceSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CreateInplaceJobRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceJobResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceMediaResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenInplaceSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.UploadInplaceMediaRequest;

/** Inplace, new flow (#/ru/myid_inplace_new_flow). */
@Service
@RequiredArgsConstructor
public class MyidInplaceService {

  private final MyidClient myid;
  private final MyidInplaceHttpClient inplaceHttp;
  private final MyidMediaHttpClient mediaHttp;
  private final MyidLongHttpClient longHttp;
  private final MyidInplaceMapper mapper;

  public OpenSessionResult openInplaceSession(OpenInplaceSessionRequest request) {
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
    var answer = myid.withToken(Grant.CLIENT, auth -> inplaceHttp.openSession(auth, session));
    return mapper.session(answer);
  }

  public InplaceMediaResult uploadInplaceMedia(UploadInplaceMediaRequest request) {
    var answer =
        myid.plain(
            false,
            () ->
                mediaHttp.upload(
                    request.getSessionId().toString(),
                    file(request.getPhotoFront(), "photo_front"),
                    request.getPhotoFocus() == null
                        ? null
                        : file(request.getPhotoFocus(), "photo_focus")));
    return mapper.media(answer);
  }

  public JobResult createInplaceJob(CreateInplaceJobRequest request) {
    var answer =
        myid.withToken(
            Grant.CLIENT,
            auth ->
                inplaceHttp.createJob(
                    auth,
                    new MyidInplaceJobRequest(
                        request.getSessionId().toString(), request.getMediaId().toString())));
    return mapper.job(answer);
  }

  /** Empty while MyID still processes the job (its 202). */
  public Optional<InplaceJobResultResult> getInplaceJobResult(UUID jobId) {
    var answer = myid.withToken(Grant.CLIENT, auth -> longHttp.inplaceJob(auth, jobId));
    return mapper.jobResult(answer);
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
