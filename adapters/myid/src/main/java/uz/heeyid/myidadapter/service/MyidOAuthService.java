package uz.heeyid.myidadapter.service;

import static uz.heeyid.myidadapter.utils.ConstantUtils.GRANT_AUTHORIZATION_CODE;
import static uz.heeyid.myidadapter.utils.ConstantUtils.QR_EXTRA_KEY;
import static uz.heeyid.myidadapter.utils.ConstantUtils.QR_EXTRA_VALUE;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.invalid;
import static uz.heeyid.myidadapter.utils.MyidRequestUtils.scope;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.heeyid.myidadapter.client.MyidTokenHttpClient;
import uz.heeyid.myidadapter.mapper.MyidOAuthMapper;
import uz.heeyid.myidadapter.model.request.MyidRefreshTokenRequest;
import uz.heeyid.myidadapter.property.MyidProperties;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ExchangeAuthorizationCodeRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.RefreshUserDataRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserDataResult;

/** Redirect (#/ru/redirect), QR inplace (#/ru/QR_inplace) and the WebSDK code. */
@Service
@RequiredArgsConstructor
public class MyidOAuthService {

  private final MyidClient myid;
  private final MyidTokenHttpClient tokenHttp;
  private final MyidOAuthMapper mapper;
  private final MyidProperties properties;

  public AuthorizationUrlResult getAuthorizationUrl(AuthorizationUrlRequest request) {
    var url =
        UriComponentsBuilder.fromUriString(properties.baseUrl())
            .path("/api/v1/oauth2/authorization")
            .queryParam("client_id", myid.credential().clientId())
            .queryParam("response_type", "code")
            .queryParamIfPresent("redirect_uri", Optional.ofNullable(request.getRedirectUri()))
            .queryParam("scope", scope(request.getScope()))
            .queryParam("method", request.getMethod().getValue())
            .queryParam("state", request.getState())
            .encode()
            .toUriString();
    return mapper.authorizationUrl(url);
  }

  public QrContentResult getQrContent(QrContentRequest request) {
    var qr =
        UriComponentsBuilder.newInstance()
            .queryParam("client_id", myid.credential().clientId())
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
    // Never null: client_id and method are always there.
    return mapper.qrContent(Objects.requireNonNull(qr.encode().build().getQuery()));
  }

  public UserDataResult exchangeAuthorizationCode(ExchangeAuthorizationCodeRequest request) {
    var credential = myid.credential();
    var token =
        myid.plain(
            true,
            () ->
                tokenHttp.authorizationCodeToken(
                    GRANT_AUTHORIZATION_CODE,
                    request.getCode(),
                    credential.clientId(),
                    credential.clientSecret(),
                    request.getMethod() == null ? null : request.getMethod().getValue(),
                    request.getScope() == null || request.getScope().isEmpty()
                        ? null
                        : scope(request.getScope())));
    return userData(token);
  }

  public UserDataResult refreshUserData(RefreshUserDataRequest request) {
    var credential = myid.credential();
    var token =
        myid.plain(
            true,
            () ->
                tokenHttp.refreshToken(
                    new MyidRefreshTokenRequest(request.getRefreshToken(), credential.clientId())));
    return userData(token);
  }

  /** The person's data with the person's token of a code or a refresh token. */
  private UserDataResult userData(Answer token) {
    var accessToken = mapper.accessToken(token);
    return mapper.userData(token, accessToken, myid.me(accessToken));
  }
}
