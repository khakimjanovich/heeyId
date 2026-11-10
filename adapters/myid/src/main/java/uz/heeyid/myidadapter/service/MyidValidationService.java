package uz.heeyid.myidadapter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidValidationHttpClient;
import uz.heeyid.myidadapter.mapper.MyidValidationMapper;
import uz.heeyid.myidadapter.model.request.MyidBnplVerifyRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationResult;

/** Identification validation (#/ru/bnpl). */
@Service
@RequiredArgsConstructor
public class MyidValidationService {

  private final MyidClient myid;
  private final MyidValidationHttpClient validationHttp;
  private final MyidValidationMapper mapper;

  public VerifyIdentificationResult verifyIdentification(VerifyIdentificationRequest request) {
    // As the contract says: JSON with no credential, as the BNPL page's example shows.
    var answer =
        myid.plain(
            false,
            () ->
                validationHttp.verify(
                    new MyidBnplVerifyRequest(
                        request.getJobId(),
                        request.getBankId(),
                        request.getBnplId(),
                        request.getPinfl())));
    return mapper.verification(answer);
  }
}
