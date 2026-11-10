package uz.heeyid.myidadapter.mapper;

import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.parse;
import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.stringOrNull;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrl;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContent;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserData;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserDataResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserProfile;

/** MyID's Redirect, QR and WebSDK-code answers as the contract's results. */
@Component
@RequiredArgsConstructor
public class MyidOAuthMapper {

  private final MyidJsonMapper json;

  public AuthorizationUrlResult authorizationUrl(String url) {
    return new AuthorizationUrlResult("", SUCCESS, new AuthorizationUrl(url));
  }

  public QrContentResult qrContent(String content) {
    return new QrContentResult("", SUCCESS, new QrContent(content));
  }

  /** The person's access token of a code or a refresh-token answer. */
  public String accessToken(Answer token) {
    return parse(token, b -> b.required("access_token").asString());
  }

  /**
   * The person's data from /users/me; {@code authentication_method} is inside MyID's profile, the
   * scope and the refresh token are in the token's answer, the job id inside the token.
   */
  public UserDataResult userData(Answer token, String accessToken, Answer me) {
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
