package uz.heeyid.myidadapter.client;

import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_CODE_NOT_VALID;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_MYID_CLIENT_ERROR;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_NOT_FOUND;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_SERVICE_UNAVAILABLE;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_UNAUTHORIZED;
import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_USER_BLOCKED;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;
import uz.heeyid.myidadapter.config.MyidProperties;
import uz.heeyid.myidadapter.config.MyidProperties.Tenant;
import uz.khakimjanovich.platform.error.ErrorExceptionStatus;
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.observability.HttpClientLogging;

/**
 * MyID over HTTP: the tenant's access token, the call, and MyID's refusals as the contract's
 * errors. No body reaches the log (they hold codes, photos and personal data); the platform's call
 * log keeps the method, the masked URI, the status and the time. The JDK client follows no
 * redirect.
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
  private final RestClient myid;
  private final RestClient media;
  private final MyidTokens tokens;
  private final MyidBreaker breaker;

  public MyidClient(
      MyidProperties properties,
      ObjectMapper mapper,
      RestClient.Builder builder,
      MyidTokens tokens,
      MyidBreaker breaker) {
    this.properties = properties;
    this.mapper = mapper;
    this.tokens = tokens;
    this.breaker = breaker;
    var requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build());
    requestFactory.setReadTimeout(properties.readTimeout());
    var base =
        builder
            .requestFactory(requestFactory)
            .defaultRequest(r -> r.attribute(HttpClientLogging.NO_BODIES, true));
    this.myid = base.clone().baseUrl(properties.baseUrl()).build();
    this.media = base.clone().baseUrl(properties.mediaUrl()).build();
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
   * A call with the tenant's token. MyID refusing the token (403) renews it once; a second 403 is
   * the contract's UNAUTHORIZED.
   */
  public Answer withToken(
      String tenantId, Grant grant, Function<RestClient, RestClient.RequestHeadersSpec<?>> call) {
    return withToken(tenantId, grant, false, call);
  }

  /** As {@link #withToken(String, Grant, Function)}; exchange true for a code exchange. */
  public Answer withToken(
      String tenantId,
      Grant grant,
      boolean exchange,
      Function<RestClient, RestClient.RequestHeadersSpec<?>> call) {
    var tenant = tenant(tenantId);
    var answer =
        send(call.apply(myid).headers(h -> h.setBearerAuth(token(tenantId, tenant, grant))));
    if (answer.status() == 403) {
      tokens.forget(tenantId, grant);
      answer = send(call.apply(myid).headers(h -> h.setBearerAuth(token(tenantId, tenant, grant))));
    }
    return checked(answer, exchange);
  }

  /**
   * A call without the tenant's token: a code exchange (exchange true: MyID refusing it means the
   * code is not valid), or a call with a person's token.
   */
  public Answer plain(
      boolean exchange, Function<RestClient, RestClient.RequestHeadersSpec<?>> call) {
    return checked(send(call.apply(myid)), exchange);
  }

  /** A call to MyID's media host, which needs no token (the Inplace session id is enough). */
  public Answer media(Function<RestClient, RestClient.RequestHeadersSpec<?>> call) {
    return checked(send(call.apply(media)), false);
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
                  myid.post()
                      .uri("/api/v1/auth/clients/access-token")
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(
                          Map.of(
                              "client_id", tenant.clientId(),
                              "client_secret", tenant.clientSecret()));
              case CLIENT_CREDENTIALS ->
                  myid.post()
                      .uri("/api/v1/oauth2/access-token")
                      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                      .body(
                          form(
                              "grant_type", "client_credentials",
                              "client_id", tenant.clientId(),
                              "client_secret", tenant.clientSecret()));
              case PASSWORD -> {
                if (tenant.username() == null || tenant.password() == null) {
                  throw new GeneralException(MYID_ADAPTER_UNAUTHORIZED)
                      .subError("credential", "the tenant has no MyID login for this flow");
                }
                yield myid.post()
                    .uri("/api/v1/oauth2/access-token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(
                        form(
                            "grant_type", "password",
                            "username", tenant.username(),
                            "password", tenant.password(),
                            "client_id", tenant.clientId()));
              }
            });
    if (answer.status() >= 500 || answer.status() == 429) {
      checked(answer, false);
    }
    if (answer.status() != 200) {
      throw new GeneralException(MYID_ADAPTER_UNAUTHORIZED)
          .subError(String.valueOf(answer.status()), detail(answer.body()));
    }
    return new MyidTokens.Token(
        answer.body().path("access_token").asString(),
        Duration.ofSeconds(answer.body().path("expires_in").asLong()));
  }

  private Answer send(RestClient.RequestHeadersSpec<?> request) {
    breaker.before();
    try {
      var entity = request.retrieve().onStatus(s -> true, (q, r) -> {}).toEntity(byte[].class);
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
                    : exchange && status >= 400
                        ? MYID_ADAPTER_CODE_NOT_VALID
                        : status == 404 ? MYID_ADAPTER_NOT_FOUND : MYID_ADAPTER_MYID_CLIENT_ERROR;
    var error =
        new GeneralException(outcome).subError(String.valueOf(status), detail(answer.body()));
    var ttl = answer.body().path("ttl");
    if (status == 429 && ttl.canConvertToLong()) {
      error.retryAfter(Duration.ofSeconds(ttl.asLong()));
    }
    throw error;
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
