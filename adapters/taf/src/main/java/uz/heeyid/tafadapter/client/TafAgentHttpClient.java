package uz.heeyid.tafadapter.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.tafadapter.model.request.TafFaceConfirmRequest;
import uz.heeyid.tafadapter.model.request.TafSendSmsRequest;

/** The TAF agent's API (taf.agent-url), integration document sections 2, 7.8 and 7.9. */
@HttpExchange(accept = "application/json")
public interface TafAgentHttpClient {

  @PostExchange(url = "/api/accounts/v1/sms", contentType = "application/json")
  ResponseEntity<byte[]> sendSms(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE) String language,
      @RequestBody TafSendSmsRequest request);

  @GetExchange("/api/accounts/v1/check-fraud/{requestId}")
  ResponseEntity<byte[]> checkFraud(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable String requestId);

  @GetExchange("/api/accounts/v1/check-fraud/{requestId}/ai")
  ResponseEntity<byte[]> checkFraudAi(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable String requestId);

  @PostExchange(url = "/api/accounts/v1/face/confirm/internal", contentType = "application/json")
  ResponseEntity<byte[]> confirmFace(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody TafFaceConfirmRequest request);

  @GetExchange("/api/accounts/v1/check-phone-owning-match")
  ResponseEntity<byte[]> phoneOwner(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam String applicationId);
}
