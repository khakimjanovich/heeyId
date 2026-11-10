package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.utils.MyidRequestUtils.invalid;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.text;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Grant;
import uz.heeyid.myidadapter.mapper.MyidSdkMapper;
import uz.heeyid.myidadapter.model.request.MyidJobIdRequest;
import uz.heeyid.myidadapter.model.request.MyidSdkSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.AddressResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetAddressRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetResultRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenFullProofingSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenReCheckSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ProofingResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.SessionStatusResult;

/** Mobile SDK, new flow (docs.myid.uz #/ru/sdknew). */
@Service
@RequiredArgsConstructor
public class MyidSdkService {

  private final MyidClient myid;
  private final MyidSdkMapper mapper;

  public OpenSessionResult openFullProofingSession(
      String tenant, OpenFullProofingSessionRequest request) {
    if (request.getPinfl() != null && request.getPassData() != null) {
      throw invalid("pinfl", "give pinfl or passData, not both");
    }
    if (request.getPassData() != null && request.getBirthDate() == null) {
      throw invalid("birthDate", "required with passData");
    }
    var session =
        MyidSdkSessionRequest.builder()
            .threshold(request.getThreshold())
            .pinfl(request.getPinfl())
            .passData(request.getPassData())
            .birthDate(text(request.getBirthDate()))
            .isResident(request.getIsResident())
            .phoneNumber(request.getPhoneNumber())
            .build();
    return openSdkSession(tenant, session);
  }

  public OpenSessionResult openReCheckSession(String tenant, OpenReCheckSessionRequest request) {
    var session =
        MyidSdkSessionRequest.builder()
            .reuid(request.getReuid().toString())
            .threshold(request.getThreshold())
            .phoneNumber(request.getPhoneNumber())
            .build();
    return openSdkSession(tenant, session);
  }

  private OpenSessionResult openSdkSession(String tenant, MyidSdkSessionRequest session) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            c ->
                c.post()
                    .uri("/api/v2/sdk/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(session));
    return mapper.session(answer);
  }

  public ProofingResultResult getResult(String tenant, GetResultRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            true,
            c -> c.get().uri("/api/v1/sdk/data?code={code}", request.getCode()));
    return mapper.result(answer);
  }

  public SessionStatusResult getSessionStatus(String tenant, UUID sessionId) {
    var answer =
        myid.withToken(
            tenant, Grant.CLIENT, c -> c.get().uri("/api/v1/sdk/sessions/{id}", sessionId));
    return mapper.sessionStatus(answer);
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
                    .body(new MyidJobIdRequest(request.getJobId().toString())));
    return mapper.address(answer);
  }
}
