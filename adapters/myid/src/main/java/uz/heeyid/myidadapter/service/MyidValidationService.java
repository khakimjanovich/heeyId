package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.client.MyidJson.parse;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidJson;
import uz.heeyid.myidadapter.model.request.MyidBnplVerifyRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.Verification;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationResult;

/** Identification validation (#/ru/bnpl). */
@Service
@RequiredArgsConstructor
public class MyidValidationService {

  private final MyidClient myid;

  public VerifyIdentificationResult verifyIdentification(
      String tenant, VerifyIdentificationRequest request) {
    // As the contract says: JSON with no credential, as the BNPL page's example shows.
    myid.tenant(tenant);
    var answer =
        myid.plain(
            false,
            c ->
                c.post()
                    .uri("/api/v1/bnpl/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        new MyidBnplVerifyRequest(
                            request.getJobId(),
                            request.getBankId(),
                            request.getBnplId(),
                            request.getPinfl())));
    var verification =
        parse(
            answer,
            b -> {
              var status = b.required("status").asInt();
              return new Verification(
                  b.required("job_id").asString(),
                  b.required("bnpl_id").asString(),
                  MyidJson.verificationStatus(status),
                  status,
                  b.required("result").asBoolean());
            });
    return new VerifyIdentificationResult(answer.requestId(), SUCCESS, verification);
  }
}
