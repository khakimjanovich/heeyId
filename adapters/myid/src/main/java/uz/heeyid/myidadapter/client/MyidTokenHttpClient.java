package uz.heeyid.myidadapter.client;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.myidadapter.model.request.MyidClientCredentialsRequest;
import uz.heeyid.myidadapter.model.request.MyidRefreshTokenRequest;

/**
 * MyID's access tokens for a tenant. MyidClient asks for them; the answer is read whatever its
 * status, so a refusal becomes the contract's error.
 */
@HttpExchange(accept = "application/json")
public interface MyidTokenHttpClient {

  /** Mobile SDK and Inplace: the client credential. */
  @PostExchange(url = "/api/v1/auth/clients/access-token", contentType = "application/json")
  ResponseEntity<byte[]> clientToken(@RequestBody MyidClientCredentialsRequest request);

  /** WebSDK (client_credentials), the Backend API (password) and a code (authorization_code). */
  @PostExchange(
      url = "/api/v1/oauth2/access-token",
      contentType = "application/x-www-form-urlencoded")
  ResponseEntity<byte[]> oauthToken(@RequestBody MultiValueMap<String, String> form);

  /** A refresh token: JSON, as Redirect and QR inplace say. */
  @PostExchange(url = "/api/v1/oauth2/refresh-token", contentType = "application/json")
  ResponseEntity<byte[]> refreshToken(@RequestBody MyidRefreshTokenRequest request);
}
