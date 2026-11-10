package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.client.MyidJson.body;
import static uz.heeyid.myidadapter.client.MyidJson.imageIssue;
import static uz.heeyid.myidadapter.client.MyidJson.parse;
import static uz.heeyid.myidadapter.client.MyidJson.reasonKey;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.invalid;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.text;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import tools.jackson.databind.node.ObjectNode;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Grant;
import uz.heeyid.myidadapter.client.MyidJson;
import uz.khakimjanovich.contracts.myidadapter.api.model.Address;
import uz.khakimjanovich.contracts.myidadapter.api.model.AddressResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetAddressRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetResultRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobAddress;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenFullProofingSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenReCheckSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ProofingResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ProofingResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.Reuid;
import uz.khakimjanovich.contracts.myidadapter.api.model.Session;
import uz.khakimjanovich.contracts.myidadapter.api.model.SessionStatus;
import uz.khakimjanovich.contracts.myidadapter.api.model.SessionStatusResult;

/** Mobile SDK, new flow (docs.myid.uz #/ru/sdknew). */
@Service
@RequiredArgsConstructor
public class MyidSdkService {

  private final MyidClient myid;
  private final MyidJson json;

  public OpenSessionResult openFullProofingSession(
      String tenant, OpenFullProofingSessionRequest request) {
    if (request.getPinfl() != null && request.getPassData() != null) {
      throw invalid("pinfl", "give pinfl or passData, not both");
    }
    if (request.getPassData() != null && request.getBirthDate() == null) {
      throw invalid("birthDate", "required with passData");
    }
    var session =
        body(
            "threshold", request.getThreshold(),
            "pinfl", request.getPinfl(),
            "pass_data", request.getPassData(),
            "birth_date", text(request.getBirthDate()),
            "is_resident", request.getIsResident(),
            "phone_number", request.getPhoneNumber());
    return openSdkSession(tenant, session);
  }

  public OpenSessionResult openReCheckSession(String tenant, OpenReCheckSessionRequest request) {
    var session =
        body(
            "reuid", request.getReuid().toString(),
            "threshold", request.getThreshold(),
            "phone_number", request.getPhoneNumber());
    return openSdkSession(tenant, session);
  }

  private OpenSessionResult openSdkSession(String tenant, Map<String, Object> session) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            c ->
                c.post()
                    .uri("/api/v2/sdk/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(session));
    return new OpenSessionResult(
        answer.requestId(), SUCCESS, parse(answer, b -> json.read(b, Session.class)));
  }

  public ProofingResultResult getResult(String tenant, GetResultRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            true,
            c -> c.get().uri("/api/v1/sdk/data?code={code}", request.getCode()));
    var result =
        parse(
            answer,
            b -> {
              var data = json.read(b.required("data"), ProofingResult.class);
              var reuid = b.path("reuid");
              if (data.getReuid() == null && reuid.isObject()) {
                data.setReuid(json.read(reuid, Reuid.class));
              }
              return data;
            });
    return new ProofingResultResult(answer.requestId(), SUCCESS, result);
  }

  public SessionStatusResult getSessionStatus(String tenant, UUID sessionId) {
    var answer =
        myid.withToken(
            tenant, Grant.CLIENT, c -> c.get().uri("/api/v1/sdk/sessions/{id}", sessionId));
    var status =
        parse(
            answer,
            b -> {
              var body = ((ObjectNode) b).deepCopy();
              body.put("status", body.required("status").asString().toUpperCase(Locale.ROOT));
              var read = json.read(body, SessionStatus.class);
              read.getAttempts()
                  .forEach(
                      a ->
                          a.reasonKey(
                                  a.getReasonCode() == null ? null : reasonKey(a.getReasonCode()))
                              .imageIssue(imageIssue(a.getReasonCode())));
              return read;
            });
    return new SessionStatusResult(answer.requestId(), SUCCESS, status);
  }

  public AddressResult getAddressByJob(String tenant, GetAddressRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            c ->
                c.post()
                    .uri("/api/v1/profile/address")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body("job_id", request.getJobId().toString())));
    var address =
        parse(
            answer,
            b ->
                new JobAddress(
                    UUID.fromString(b.required("response_id").asString()),
                    json.read(b.required("profile").required("address"), Address.class)));
    return new AddressResult(answer.requestId(), SUCCESS, address);
  }
}
