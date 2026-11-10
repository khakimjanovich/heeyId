package uz.heeyid.myidadapter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.mapper.MyidValidationMapper;
import uz.heeyid.myidadapter.model.request.MyidBnplVerifyRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationResult;

/** Identification validation (#/ru/bnpl). */
@Service
@RequiredArgsConstructor
public class MyidValidationService {

  private final MyidClient myid;
  private final MyidValidationMapper mapper;

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
    return mapper.verification(answer);
  }
}
