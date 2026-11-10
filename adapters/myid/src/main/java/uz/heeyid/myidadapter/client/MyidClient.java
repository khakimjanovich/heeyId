package uz.heeyid.myidadapter.client;

import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_CODE_NOT_VALID;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_MYID_CLIENT_ERROR;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_NOT_FOUND;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_SERVICE_UNAVAILABLE;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_UNAUTHORIZED;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_USER_BLOCKED;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;
import uz.heeyid.myidadapter.model.request.MyidClientCredentialsRequest;
import uz.heeyid.myidadapter.property.MyidProperties;
import uz.heeyid.myidadapter.property.MyidProperties.Tenant;
import uz.khakimjanovich.platform.error.ErrorExceptionStatus;
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.error.StatusThrowable;

/**
 * MyID's calls (the @HttpExchange clients beside it, built in MyidHttpClientConfig) with the
 * tenant's access token, the breaker, and MyID's refusals as the contract's errors.
 */
@Component
public class MyidClient {

  /** Which MyID access token a call needs. */
  public enum Grant {
    /** /api/v1/auth/clients/access-token: Mobile SDK and Inplace. */
    CLIENT,
    /** /api/v1/oauth2/access-token, client_credentials: WebSDK. */
    CLIENT_CREDENTIALS,
    /** /api/v1/oauth2/access-token, password: the Backend API without personal data. */
    PASSWORD
  }

  /** MyID's answer: its body (missing when empty), its X-Request-ID and its HTTP status. */
  public record Answer(JsonNode body, String requestId, int status) {}

  private final MyidProperties properties;
  private final ObjectMapper mapper;
  private final MyidTokenHttpClient tokenHttp;
  private final MyidLongHttpClient longHttp;
  private final MyidTokens tokens;
  private final MyidBreaker breaker;

  public MyidClient(
      MyidProperties properties,
      ObjectMapper mapper,
      MyidTokenHttpClient tokenHttp,
      MyidLongHttpClient longHttp,
      MyidTokens tokens,
      MyidBreaker breaker) {
    this.properties = properties;
    this.mapper = mapper;
    this.tokenHttp = tokenHttp;
    this.longHttp = longHttp;
    this.tokens = tokens;
    this.breaker = breaker;
  }

  /** The tenant of X-Tenant-Id; an unknown one is the caller's mistake. */
  public Tenant tenant(String id) {
    var tenant = properties.tenants().get(id);
    if (tenant == null) {
      throw new GeneralException(ErrorExceptionStatus.VALIDATION_FAILED)
          .subError("X-Tenant-Id", "unknown tenant");
    }
    return tenant;
  }

  /**
   * A call with the tenant's token: {@code call} gets the Authorization value. MyID refusing the
   * token (403) renews it once; a second 403 is the contract's UNAUTHORIZED.
   */
  public Answer withToken(
      String tenantId, Grant grant, Function<String, ResponseEntity<byte[]>> call) {
    return withToken(tenantId, grant, false, call);
  }

  /** As {@link #withToken(String, Grant, Function)}; exchange true for a code exchange. */
  public Answer withToken(
      String tenantId,
      Grant grant,
      boolean exchange,
      Function<String, ResponseEntity<byte[]>> call) {
    var tenant = tenant(tenantId);
    var answer = send(() -> call.apply(bearer(token(tenantId, tenant, grant))));
    if (answer.status() == 403) {
      tokens.forget(tenantId, grant);
      answer = send(() -> call.apply(bearer(token(tenantId, tenant, grant))));
    }
    return checked(answer, exchange);
  }

  /**
   * A call without the tenant's token: a code exchange (exchange true: MyID refusing it means the
   * code is not valid), the BNPL validation, or the media host.
   */
  public Answer plain(boolean exchange, Supplier<ResponseEntity<byte[]>> call) {
    return checked(send(call), exchange);
  }

  /**
   * /api/v1/users/me with the person's token; MyID answers it in up to 30 s. A 403 refuses the
   * person's token, not the tenant's credential: MYID_CLIENT_ERROR, not UNAUTHORIZED.
   */
  public Answer me(String accessToken) {
    var answer = send(() -> longHttp.me(bearer(accessToken)));
    if (answer.status() == 403) {
      throw refusal(MYID_ADAPTER_MYID_CLIENT_ERROR, answer);
    }
    return checked(answer, false);
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private String token(String tenantId, Tenant tenant, Grant grant) {
    return tokens.get(tenantId, grant, () -> issue(tenant, grant));
  }

  /** A new token from MyID; MyID refusing the credential is the contract's UNAUTHORIZED. */
  private MyidTokens.Token issue(Tenant tenant, Grant grant) {
    var answer =
        send(
            switch (grant) {
              case CLIENT ->
                  () ->
                      tokenHttp.clientToken(
                          new MyidClientCredentialsRequest(
                              tenant.clientId(), tenant.clientSecret()));
              case CLIENT_CREDENTIALS ->
                  () ->
                      tokenHttp.oauthToken(
                          form(
                              "grant_type", "client_credentials",
                              "client_id", tenant.clientId(),
                              "client_secret", tenant.clientSecret()));
              case PASSWORD -> {
                if (tenant.username() == null || tenant.password() == null) {
                  throw new GeneralException(MYID_ADAPTER_UNAUTHORIZED)
                      .subError("credential", "the tenant has no MyID login for this flow");
                }
                yield () ->
                    tokenHttp.oauthToken(
                        form(
                            "grant_type", "password",
                            "username", tenant.username(),
                            "password", tenant.password(),
                            "client_id", tenant.clientId()));
              }
            });
    if (answer.status() >= 500 || answer.status() == 429 || answer.status() == 422) {
      checked(answer, false);
    }
    if (answer.status() != 200) {
      throw refusal(MYID_ADAPTER_UNAUTHORIZED, answer);
    }
    return new MyidTokens.Token(
        answer.body().path("access_token").asString(),
        Duration.ofSeconds(answer.body().path("expires_in").asLong()));
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
      var requestId = entity.getHeaders().getFirst("X-Request-ID");
      return new Answer(json(entity.getBody()), requestId == null ? "" : requestId, status);
    } catch (ResourceAccessException e) {
      breaker.failed();
      throw new GeneralException(MYID_ADAPTER_SERVICE_UNAVAILABLE)
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

  /** A 2xx goes back to the caller; anything else is the contract's error for it. */
  public static Answer checked(Answer answer, boolean exchange) {
    var status = answer.status();
    if (status >= 200 && status < 300) {
      return answer;
    }
    var outcome =
        status >= 500
            ? MYID_ADAPTER_SERVICE_UNAVAILABLE
            : status == 429
                ? MYID_ADAPTER_USER_BLOCKED
                : status == 403
                    ? MYID_ADAPTER_UNAUTHORIZED
                    : status == 422
                        ? MYID_ADAPTER_MYID_CLIENT_ERROR
                        : exchange && status >= 400
                            ? MYID_ADAPTER_CODE_NOT_VALID
                            : status == 404
                                ? MYID_ADAPTER_NOT_FOUND
                                : MYID_ADAPTER_MYID_CLIENT_ERROR;
    var error = refusal(outcome, answer);
    var ttl = answer.body().path("ttl");
    if (status == 429 && ttl.canConvertToLong()) {
      error.retryAfter(Duration.ofSeconds(ttl.asLong()));
    }
    throw error;
  }

  /** The contract's error for MyID's answer: MyID's status and detail, then its X-Request-ID. */
  public static GeneralException refusal(StatusThrowable outcome, Answer answer) {
    return new GeneralException(outcome)
        .subError(String.valueOf(answer.status()), detail(answer.body()))
        .subError("X-Request-ID", answer.requestId());
  }

  private static @Nullable String detail(JsonNode body) {
    if (body.isString()) {
      return body.asString();
    }
    var detail = body.path("detail");
    if (detail.isMissingNode() || detail.isNull()) {
      return null;
    }
    return detail.isString() ? detail.asString() : detail.toString();
  }

  /** A form body from name, value pairs; a null value is left out. */
  public static LinkedMultiValueMap<String, String> form(@Nullable String... pairs) {
    var form = new LinkedMultiValueMap<String, String>();
    for (var i = 0; i < pairs.length; i += 2) {
      if (pairs[i + 1] != null) {
        form.add(Objects.requireNonNull(pairs[i]), pairs[i + 1]);
      }
    }
    return form;
  }
}
