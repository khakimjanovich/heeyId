package uz.heeyid.myidadapter.client;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.myidadapter.model.request.MyidClientCredentialsRequest;
import uz.heeyid.myidadapter.model.request.MyidRefreshTokenRequest;

/**
 * MyID's access tokens for the adapter. MyidClient asks for them; the answer is read whatever its
 * status, so a refusal becomes the contract's error.
 */
@HttpExchange(accept = "application/json")
public interface MyidTokenHttpClient {

  /** Mobile SDK and Inplace: the client credential. */
  @PostExchange(url = "/api/v1/auth/clients/access-token", contentType = "application/json")
  ResponseEntity<byte[]> clientToken(@RequestBody MyidClientCredentialsRequest request);

  /** WebSDK: the client_credentials grant. Each parameter is a form field. */
  @PostExchange(
      url = "/api/v1/oauth2/access-token",
      contentType = "application/x-www-form-urlencoded")
  ResponseEntity<byte[]> clientCredentialsToken(
      @RequestParam("grant_type") String grantType,
      @RequestParam("client_id") String clientId,
      @RequestParam("client_secret") String clientSecret);

  /** The Backend API without personal data: the password grant. */
  @PostExchange(
      url = "/api/v1/oauth2/access-token",
      contentType = "application/x-www-form-urlencoded")
  ResponseEntity<byte[]> passwordToken(
      @RequestParam("grant_type") String grantType,
      @RequestParam("username") String username,
      @RequestParam("password") String password,
      @RequestParam("client_id") String clientId);

  /** Redirect, QR and WebSDK: a person's code. A null method or scope is left out. */
  @PostExchange(
      url = "/api/v1/oauth2/access-token",
      contentType = "application/x-www-form-urlencoded")
  ResponseEntity<byte[]> authorizationCodeToken(
      @RequestParam("grant_type") String grantType,
      @RequestParam("code") String code,
      @RequestParam("client_id") String clientId,
      @RequestParam("client_secret") String clientSecret,
      @RequestParam(name = "method", required = false) @Nullable String method,
      @RequestParam(name = "scope", required = false) @Nullable String scope);

  /** A refresh token: JSON, as Redirect and QR inplace say. */
  @PostExchange(url = "/api/v1/oauth2/refresh-token", contentType = "application/json")
  ResponseEntity<byte[]> refreshToken(@RequestBody MyidRefreshTokenRequest request);
}
