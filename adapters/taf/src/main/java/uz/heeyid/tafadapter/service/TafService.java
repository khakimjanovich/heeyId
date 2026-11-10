package uz.heeyid.tafadapter.service;

import static uz.heeyid.tafadapter.utils.ConstantUtils.TEMPLATE_LOGIN;
import static uz.heeyid.tafadapter.utils.ConstantUtils.TEMPLATE_REGISTRATION;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uz.heeyid.tafadapter.client.TafAgentHttpClient;
import uz.heeyid.tafadapter.client.TafClient;
import uz.heeyid.tafadapter.client.TafHealthHttpClient;
import uz.heeyid.tafadapter.mapper.TafMapper;
import uz.heeyid.tafadapter.model.request.TafFaceConfirmRequest;
import uz.heeyid.tafadapter.model.request.TafSendSmsRequest;
import uz.khakimjanovich.contracts.tafadapter.api.model.BasicVerdict;
import uz.khakimjanovich.contracts.tafadapter.api.model.CodeType;
import uz.khakimjanovich.contracts.tafadapter.api.model.ConfirmFaceRequest;
import uz.khakimjanovich.contracts.tafadapter.api.model.Health;
import uz.khakimjanovich.contracts.tafadapter.api.model.PhoneOwner;
import uz.khakimjanovich.contracts.tafadapter.api.model.RiskVerdict;
import uz.khakimjanovich.contracts.tafadapter.api.model.SendCodeRequest;
import uz.khakimjanovich.platform.error.ErrorExceptionStatus;
import uz.khakimjanovich.platform.error.GeneralException;

/** The TAF calls of a registration and a login. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TafService {

  private final TafClient taf;
  private final TafAgentHttpClient agent;
  private final TafHealthHttpClient healthHttp;
  private final TafMapper mapper;

  /** Not repeated on any failure: each call sends an SMS. */
  public void sendCode(SendCodeRequest request) {
    var login = request.getType() == CodeType.LOGIN;
    if (login && request.getDeviceModel() == null) {
      throw new GeneralException(ErrorExceptionStatus.VALIDATION_FAILED)
          .subError("deviceModel", "required for LOGIN");
    }
    var sms =
        new TafSendSmsRequest(
            phone(request.getPhone()),
            request.getRequestId().toString(),
            request.getCode(),
            login ? TEMPLATE_LOGIN : TEMPLATE_REGISTRATION,
            login ? request.getDeviceModel() : null);
    taf.withToken(auth -> agent.sendSms(auth, request.getLang().getValue(), sms));
  }

  public BasicVerdict basicVerdict(UUID requestId) {
    return mapper.basicVerdict(taf.withToken(auth -> agent.checkFraud(auth, requestId.toString())));
  }

  public RiskVerdict riskVerdict(UUID requestId) {
    return mapper.riskVerdict(
        taf.withToken(auth -> agent.checkFraudAi(auth, requestId.toString())));
  }

  public void confirmFace(ConfirmFaceRequest request) {
    var face = new TafFaceConfirmRequest(request.getPinfl(), phone(request.getPhone()));
    taf.withToken(auth -> agent.confirmFace(auth, face));
  }

  public PhoneOwner phoneOwner(String applicationId) {
    return mapper.phoneOwner(taf.withToken(auth -> agent.phoneOwner(auth, applicationId)));
  }

  /** DOWN on any failure: no answer, a refusal, a state other than UP, or an open breaker. */
  public Health health() {
    try {
      return mapper.health(taf.withToken(healthHttp::health));
    } catch (GeneralException e) {
      log.warn("TAF health: down | outcome: {}", e.getMessage());
      return new Health(Health.StatusEnum.DOWN);
    }
  }

  /** TAF takes the phone as 12 digits, without +. */
  private static String phone(String phone) {
    return phone.startsWith("+") ? phone.substring(1) : phone;
  }
}
