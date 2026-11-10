package uz.heeyid.tafadapter.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.heeyid.tafadapter.service.TafService;
import uz.khakimjanovich.contracts.tafadapter.api.TafServiceApi;
import uz.khakimjanovich.contracts.tafadapter.api.model.BasicVerdict;
import uz.khakimjanovich.contracts.tafadapter.api.model.ConfirmFaceRequest;
import uz.khakimjanovich.contracts.tafadapter.api.model.Health;
import uz.khakimjanovich.contracts.tafadapter.api.model.PhoneOwner;
import uz.khakimjanovich.contracts.tafadapter.api.model.RiskVerdict;
import uz.khakimjanovich.contracts.tafadapter.api.model.SendCodeRequest;

/** Every operation of taf-adapter-api. */
@RestController
@RequiredArgsConstructor
public class TafAdapterController implements TafServiceApi {

  private final TafService taf;

  @Override
  public ResponseEntity<Void> sendCode(SendCodeRequest request) {
    taf.sendCode(request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<BasicVerdict> getBasicVerdict(UUID requestId) {
    return ResponseEntity.ok(taf.basicVerdict(requestId));
  }

  @Override
  public ResponseEntity<RiskVerdict> getRiskVerdict(UUID requestId) {
    return ResponseEntity.ok(taf.riskVerdict(requestId));
  }

  @Override
  public ResponseEntity<Void> confirmFace(ConfirmFaceRequest request) {
    taf.confirmFace(request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<PhoneOwner> getPhoneOwner(String applicationId) {
    return ResponseEntity.ok(taf.phoneOwner(applicationId));
  }

  @Override
  public ResponseEntity<Health> getHealth() {
    return ResponseEntity.ok(taf.health());
  }
}
