package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.client.MyidClient.form;
import static uz.heeyid.myidadapter.client.MyidJson.parse;
import static uz.heeyid.myidadapter.utils.ConstantUtils.QR_EXTRA_KEY;
import static uz.heeyid.myidadapter.utils.ConstantUtils.QR_EXTRA_VALUE;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.invalid;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.scope;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.stringOrNull;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.node.ObjectNode;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.heeyid.myidadapter.client.MyidJson;
import uz.heeyid.myidadapter.model.request.MyidRefreshTokenRequest;
import uz.heeyid.myidadapter.property.MyidProperties;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrl;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ExchangeAuthorizationCodeRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContent;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.RefreshUserDataRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserData;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserDataResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserProfile;

/** Redirect (#/ru/redirect), QR inplace (#/ru/QR_inplace) and the WebSDK code. */
@Service
@RequiredArgsConstructor
public class MyidOAuthService {

  private final MyidClient myid;
  private final MyidJson json;
  private final MyidProperties properties;

  public AuthorizationUrlResult getAuthorizationUrl(
      String tenant, AuthorizationUrlRequest request) {
    var url =
        UriComponentsBuilder.fromUriString(properties.baseUrl())
            .path("/api/v1/oauth2/authorization")
            .queryParam("client_id", myid.tenant(tenant).clientId())
            .queryParam("response_type", "code")
            .queryParamIfPresent("redirect_uri", Optional.ofNullable(request.getRedirectUri()))
            .queryParam("scope", scope(request.getScope()))
            .queryParam("method", request.getMethod().getValue())
            .queryParam("state", request.getState())
            .encode()
            .toUriString();
    return new AuthorizationUrlResult("", SUCCESS, new AuthorizationUrl(url));
  }

  public QrContentResult getQrContent(String tenant, QrContentRequest request) {
    var qr =
        UriComponentsBuilder.newInstance()
            .queryParam("client_id", myid.tenant(tenant).clientId())
            .queryParam("method", request.getMethod().getValue());
    var extraInfo =
        request.getExtraInfo() == null ? Map.<String, String>of() : request.getExtraInfo();
    if (extraInfo.size() > 10) {
      throw invalid("extraInfo", "at most 10 pairs");
    }
    extraInfo.forEach(
        (key, value) -> {
          if (key.equals("client_id")
              || key.equals("method")
              || !QR_EXTRA_KEY.matcher(key).matches()
              || value == null
              || !QR_EXTRA_VALUE.matcher(value).matches()) {
            throw invalid(
                "extraInfo",
                "a key matches ^[A-Za-z0-9_-]{1,50}$ and is not client_id or method; "
                    + "a value matches ^[A-Za-z0-9._~-]{1,100}$");
          }
          qr.queryParam(key, value);
        });
    return new QrContentResult("", SUCCESS, new QrContent(qr.encode().build().getQuery()));
  }

  public UserDataResult exchangeAuthorizationCode(
      String tenant, ExchangeAuthorizationCodeRequest request) {
    var credential = myid.tenant(tenant);
    var token =
        myid.plain(
            true,
            c ->
                c.post()
                    .uri("/api/v1/oauth2/access-token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(
                        form(
                            "grant_type",
                            "authorization_code",
                            "code",
                            request.getCode(),
                            "client_id",
                            credential.clientId(),
                            "client_secret",
                            credential.clientSecret(),
                            "method",
                            request.getMethod() == null ? null : request.getMethod().getValue(),
                            "scope",
                            request.getScope() == null || request.getScope().isEmpty()
                                ? null
                                : scope(request.getScope()))));
    return userData(token);
  }

  public UserDataResult refreshUserData(String tenant, RefreshUserDataRequest request) {
    var credential = myid.tenant(tenant);
    var token =
        myid.plain(
            true,
            c ->
                c.post()
                    .uri("/api/v1/oauth2/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        new MyidRefreshTokenRequest(
                            request.getRefreshToken(), credential.clientId())));
    return userData(token);
  }

  /** The person's data with the person's token of a code or a refresh token. */
  private UserDataResult userData(Answer token) {
    var accessToken = parse(token, b -> b.required("access_token").asString());
    var me = myid.me(accessToken);
    var data =
        parse(
            me,
            b -> {
              var profile = ((ObjectNode) b.required("profile")).deepCopy();
              var method = profile.remove("authentication_method");
              return new UserData()
                  .jobId(json.jobIdOf(accessToken))
                  .scope(stringOrNull(token.body().path("scope")))
                  .authenticationMethod(method == null ? null : stringOrNull(method))
                  .refreshToken(stringOrNull(token.body().path("refresh_token")))
                  .profile(json.read(profile, UserProfile.class));
            });
    return new UserDataResult(me.requestId(), SUCCESS, data);
  }
}
