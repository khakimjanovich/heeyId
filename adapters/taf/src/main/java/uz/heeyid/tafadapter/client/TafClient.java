package uz.heeyid.tafadapter.client;

import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_CODE_ALREADY_SENT;
import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_DEVICE_NOT_SUPPORTED;
import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_NOT_FOUND;
import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_NUMBER_BLOCKED;
import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_SERVICE_UNAVAILABLE;
import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_TAF_CLIENT_ERROR;
import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_TRUSTED_BANK_REFUSED;
import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_UNAUTHORIZED;
import static uz.heeyid.tafadapter.utils.ConstantUtils.GRANT_CLIENT_CREDENTIALS;
import static uz.heeyid.tafadapter.utils.ConstantUtils.TAF_CODES_NOT_FOUND;
import static uz.heeyid.tafadapter.utils.ConstantUtils.TAF_CODES_UNSUPPORTED_DEVICE;
import static uz.heeyid.tafadapter.utils.ConstantUtils.TAF_CODE_ALREADY_SENT;
import static uz.heeyid.tafadapter.utils.ConstantUtils.TAF_CODE_MY_GOV_FORBIDDEN;
import static uz.heeyid.tafadapter.utils.ConstantUtils.TAF_CODE_USER_BLOCKED;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;
import uz.heeyid.tafadapter.property.TafProperties;
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.error.StatusThrowable;

/**
 * TAF's calls (the @HttpExchange clients beside it, built in TafHttpClientConfig) with the
 * adapter's access token, the breaker, and TAF's refusals as the contract's errors.
 */
@Component
public class TafClient {

  /** TAF's answer: its body (missing when empty) and its HTTP status. */
  public record Answer(JsonNode body, int status) {}

  private final TafProperties properties;
  private final ObjectMapper mapper;
  private final TafTokenHttpClient tokenHttp;
  private final TafTokens tokens;
  private final TafBreaker breaker;

  public TafClient(
      TafProperties properties,
      ObjectMapper mapper,
      TafTokenHttpClient tokenHttp,
      TafTokens tokens,
      TafBreaker breaker) {
    this.properties = properties;
    this.mapper = mapper;
    this.tokenHttp = tokenHttp;
    this.tokens = tokens;
    this.breaker = breaker;
  }

  /**
   * A call with the adapter's token: {@code call} gets the Authorization value. TAF refusing the
   * token (401) renews it once; a second 401 is the contract's UNAUTHORIZED.
   */
  public Answer withToken(Function<String, ResponseEntity<byte[]>> call) {
    // The token first, outside the call's breaker step: getting it is a TAF call of its own
    // (counted once, in issue), and a refused credential or a Valkey error is no TAF outage.
    var auth = bearer(tokens.get(this::issue));
    var answer = send(() -> call.apply(auth));
    if (answer.status() == 401) {
      tokens.forget();
      var renewed = bearer(tokens.get(this::issue));
      answer = send(() -> call.apply(renewed));
    }
    return checked(answer);
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  /** A new token from TAF; TAF refusing the credential is the contract's UNAUTHORIZED. */
  private TafTokens.Token issue() {
    var credential = properties.credential();
    var answer =
        send(
            () ->
                tokenHttp.token(
                    GRANT_CLIENT_CREDENTIALS,
                    credential.clientId(),
                    credential.clientSecret(),
                    credential.scope()));
    if (answer.status() >= 500) {
      checked(answer);
    }
    var token = answer.body().path("access_token");
    var expiresIn = answer.body().path("expires_in");
    if (answer.status() != 200 || !token.isString() || !expiresIn.canConvertToLong()) {
      throw refusal(TAF_ADAPTER_UNAUTHORIZED, answer);
    }
    return new TafTokens.Token(token.asString(), Duration.ofSeconds(expiresIn.asLong()));
  }

  private Answer send(Supplier<ResponseEntity<byte[]>> call) {
    breaker.before();
    try {
      var entity = call.get();
      var status = entity.getStatusCode().value();
      if (status >= 500) {
        breaker.failed();
      } else {
        breaker.answered();
      }
      return new Answer(json(entity.getBody()), status);
    } catch (ResourceAccessException e) {
      breaker.failed();
      throw new GeneralException(TAF_ADAPTER_SERVICE_UNAVAILABLE)
          .subError(e.getClass().getSimpleName(), e.getMessage());
    } catch (RuntimeException e) {
      // Any other failure ends a probe too; else the breaker would stay open for good.
      breaker.failed();
      throw e;
    }
  }

  private JsonNode json(byte @Nullable [] bytes) {
    if (bytes == null || bytes.length == 0) {
      return mapper.missingNode();
    }
    try {
      return mapper.readTree(bytes);
    } catch (RuntimeException e) {
      // An answer that is not JSON (an HTML error page): its text is the detail.
      return StringNode.valueOf(new String(bytes, StandardCharsets.UTF_8));
    }
  }

  /** A 2xx goes back to the caller; anything else is the contract's error for TAF's code. */
  public static Answer checked(Answer answer) {
    var status = answer.status();
    if (status >= 200 && status < 300) {
      return answer;
    }
    throw refusal(outcome(status, answer.body().path("code")), answer);
  }

  static StatusThrowable outcome(int status, JsonNode code) {
    if (status >= 500) {
      return TAF_ADAPTER_SERVICE_UNAVAILABLE;
    }
    if (status == 401) {
      return TAF_ADAPTER_UNAUTHORIZED;
    }
    if (!code.canConvertToInt()) {
      return TAF_ADAPTER_TAF_CLIENT_ERROR;
    }
    var taf = code.asInt();
    if (taf == TAF_CODE_ALREADY_SENT) {
      return TAF_ADAPTER_CODE_ALREADY_SENT;
    }
    if (taf == TAF_CODE_USER_BLOCKED) {
      return TAF_ADAPTER_NUMBER_BLOCKED;
    }
    if (taf == TAF_CODE_MY_GOV_FORBIDDEN) {
      return TAF_ADAPTER_TRUSTED_BANK_REFUSED;
    }
    if (TAF_CODES_UNSUPPORTED_DEVICE.contains(taf)) {
      return TAF_ADAPTER_DEVICE_NOT_SUPPORTED;
    }
    if (TAF_CODES_NOT_FOUND.contains(taf)) {
      return TAF_ADAPTER_NOT_FOUND;
    }
    return TAF_ADAPTER_TAF_CLIENT_ERROR;
  }

  /** The contract's error for TAF's answer: TAF's status, then its code and message. */
  public static GeneralException refusal(StatusThrowable outcome, Answer answer) {
    return new GeneralException(outcome).subError(String.valueOf(answer.status()), detail(answer));
  }

  private static @Nullable String detail(Answer answer) {
    var body = answer.body();
    if (body.isString()) {
      return body.asString();
    }
    var code = body.path("code");
    var message = body.path("message");
    if (code.isMissingNode() && message.isMissingNode()) {
      return null;
    }
    return code.asString("") + ": " + message.asString("");
  }
}
