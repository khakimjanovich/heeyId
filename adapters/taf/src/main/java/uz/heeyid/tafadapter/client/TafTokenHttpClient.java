package uz.heeyid.tafadapter.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** TAF's OAuth 2.0 token endpoint (taf.auth-url); each parameter is a form field. */
@HttpExchange(accept = "application/json")
public interface TafTokenHttpClient {

  @PostExchange(contentType = "application/x-www-form-urlencoded")
  ResponseEntity<byte[]> token(
      @RequestParam("grant_type") String grantType,
      @RequestParam("client_id") String clientId,
      @RequestParam("client_secret") String clientSecret,
      @RequestParam("scope") String scope);
}
