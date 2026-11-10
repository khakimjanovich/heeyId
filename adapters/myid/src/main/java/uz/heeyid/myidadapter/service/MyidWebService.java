package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.client.MyidJson.imageIssue;
import static uz.heeyid.myidadapter.client.MyidJson.parse;
import static uz.heeyid.myidadapter.client.MyidJson.reasonKey;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Grant;
import uz.heeyid.myidadapter.client.MyidJson;
import uz.heeyid.myidadapter.model.request.MyidCloseWebSessionRequest;
import uz.heeyid.myidadapter.model.request.MyidWebSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CloseWebSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.EmptyResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenWebSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.Session;
import uz.khakimjanovich.contracts.myidadapter.api.model.WebSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.WebSessionResultResult;

/** WebSDK (#/ru/websdk). */
@Service
@RequiredArgsConstructor
public class MyidWebService {

  private final MyidClient myid;
  private final MyidJson json;

  public OpenSessionResult openWebSession(String tenant, OpenWebSessionRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT_CREDENTIALS,
            c ->
                c.post()
                    .uri("/api/v1/web/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        new MyidWebSessionRequest(
                            request.getMaxRetries(),
                            request.getExternalId().toString(),
                            request.getIpAddress())));
    return new OpenSessionResult(
        answer.requestId(), SUCCESS, parse(answer, b -> json.read(b, Session.class)));
  }

  public EmptyResult closeWebSession(
      String tenant, UUID sessionId, CloseWebSessionRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT_CREDENTIALS,
            c ->
                c.post()
                    .uri("/api/v1/web/sessions/{id}/client/close", sessionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MyidCloseWebSessionRequest(request.getCode())));
    return new EmptyResult(answer.requestId(), SUCCESS);
  }

  public WebSessionResultResult getWebSessionResult(String tenant, UUID sessionId) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT_CREDENTIALS,
            c -> c.post().uri("/api/v1/web/sessions/{id}/result", sessionId));
    var result =
        parse(
            answer,
            b -> {
              var read = json.read(b, WebSessionResult.class);
              read.getJobs()
                  .forEach(
                      j ->
                          j.reasonKey(reasonKey(j.getResultCode()))
                              .imageIssue(imageIssue(j.getResultCode())));
              return read;
            });
    return new WebSessionResultResult(answer.requestId(), SUCCESS, result);
  }
}
