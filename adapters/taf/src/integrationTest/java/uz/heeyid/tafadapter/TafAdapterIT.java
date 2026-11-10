package uz.heeyid.tafadapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.UUID;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.testcontainers.containers.GenericContainer;
import uz.heeyid.tafadapter.client.TafBreaker;
import uz.khakimjanovich.contracts.tafadapter.api.client.TafServiceClient;
import uz.khakimjanovich.contracts.tafadapter.api.model.CodeType;
import uz.khakimjanovich.contracts.tafadapter.api.model.ConfirmFaceRequest;
import uz.khakimjanovich.contracts.tafadapter.api.model.Decision;
import uz.khakimjanovich.contracts.tafadapter.api.model.Health;
import uz.khakimjanovich.contracts.tafadapter.api.model.Language;
import uz.khakimjanovich.contracts.tafadapter.api.model.SendCodeRequest;

/**
 * The adapter as heeyID's gateways see it: its generated client over mutual TLS, TAF (token
 * endpoint, agent API and agent health) simulated by WireMock with a 1 second read timeout.
 */
// The random management port is read before the dynamic properties exist.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "TAF_ADAPTER_MANAGEMENT_PORT=0")
class TafAdapterIT {

  static final UUID REQUEST = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-1234567890ef");
  static final String TOKEN_URL = "/oauth2/token";
  static final String SMS = "/api/accounts/v1/sms";
  static final String FRAUD = "/api/accounts/v1/check-fraud/" + REQUEST;
  static final String FACE = "/api/accounts/v1/face/confirm/internal";
  static final String OWNER = "/api/accounts/v1/check-phone-owning-match";
  static final String HEALTH = "/actuator/health";

  /** TAF's own CA: the adapter trusts TAF only through it (TAF_TRUSTED_CAS). */
  static final ThrowawayCa TAF_CA = new ThrowawayCa(Callers.directory(), "taf");

  static final WireMockServer TAF = new WireMockServer(tls(TAF_CA.server("taf")));

  /** A TAF host whose certificate comes from another CA: the adapter must refuse it. */
  static final WireMockServer FALSE_TAF =
      new WireMockServer(tls(Callers.HEEYID_CA.server("false-taf")));

  static final String TOKEN_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  /** Valkey with an ACL: the adapter's user reaches only keys taf-adapter:*. */
  static final GenericContainer<?> VALKEY =
      new GenericContainer<>("valkey/valkey:8.1-alpine")
          .withCommand(
              "valkey-server",
              "--user",
              "default",
              "off",
              "--user",
              "taf-adapter",
              "on",
              ">valkey-it",
              "resetkeys",
              "~taf-adapter:*",
              "resetchannels",
              "-@all",
              "+get",
              "+set",
              "+del",
              "+ping",
              "+info",
              "+hello",
              "+client|setinfo",
              "--user",
              "it-admin",
              "on",
              ">admin-it",
              "~*",
              "+@all")
          .withExposedPorts(6379);

  static {
    TAF.start();
    FALSE_TAF.start();
    VALKEY.start();
  }

  @DynamicPropertySource
  static void taf(DynamicPropertyRegistry registry) {
    Callers.register(registry);
    registry.add("TAF_AUTH_URL", () -> tafUrl() + TOKEN_URL);
    registry.add("TAF_AGENT_URL", TafAdapterIT::tafUrl);
    registry.add("TAF_HEALTH_URL", () -> tafUrl() + HEALTH);
    registry.add("TAF_TRUSTED_CAS", () -> "file:" + TAF_CA.certificate());
    registry.add("TAF_ADAPTER_TOKEN_KEY", () -> TOKEN_KEY);
    registry.add("TAF_CLIENT_ID", () -> "bank-one");
    registry.add("TAF_CLIENT_SECRET", () -> "secret-one");
    registry.add("TAF_SCOPE", () -> "antifraud");
    registry.add("TAF_CONNECT_TIMEOUT", () -> "1s");
    registry.add("TAF_READ_TIMEOUT", () -> "1s");
    registry.add("TAF_BREAKER_FAILURES", () -> "5");
    registry.add("TAF_BREAKER_PROBE_AFTER", () -> "30s");
    registry.add("VALKEY_HOST", VALKEY::getHost);
    registry.add("VALKEY_PORT", () -> VALKEY.getMappedPort(6379));
    registry.add("VALKEY_USERNAME", () -> "taf-adapter");
    registry.add("VALKEY_PASSWORD", () -> "valkey-it");
    registry.add("TRACING_SAMPLING", () -> "0.0"); // no collector here: nothing is traced
    registry.add("OTLP_TRACES_ENDPOINT", () -> "http://otlp.not-started.invalid/v1/traces");
    registry.add("OTLP_METRICS_ENDPOINT", () -> "http://otlp.not-started.invalid/v1/metrics");
    registry.add("OTLP_LOGS_ENDPOINT", () -> "http://otlp.not-started.invalid/v1/logs");
  }

  static WireMockConfiguration tls(ThrowawayCa.Party server) {
    return options()
        .httpDisabled(true)
        .dynamicHttpsPort()
        .keystorePath(server.keyStore().toString())
        .keystoreType("PKCS12")
        .keystorePassword("throwaway")
        .keyManagerPassword("throwaway");
  }

  static String tafUrl() {
    return "https://localhost:" + TAF.httpsPort();
  }

  @LocalServerPort int port;

  TafServiceClient adapter;

  /** Valkey as it-admin: the adapter's own user may not list or read TTLs. */
  StringRedisTemplate valkey;

  @Autowired SslBundles sslBundles;
  @Autowired TafBreaker breaker;

  @BeforeEach
  void client() {
    TAF.resetAll();
    breaker.answered(); // a test that opens the breaker leaves it closed for the next one
    if (valkey == null) {
      var admin = new RedisStandaloneConfiguration(VALKEY.getHost(), VALKEY.getMappedPort(6379));
      admin.setUsername("it-admin");
      admin.setPassword("admin-it");
      var factory = new LettuceConnectionFactory(admin);
      factory.afterPropertiesSet();
      factory.start();
      valkey = new StringRedisTemplate(factory);
    }
    valkey.delete(valkey.keys("*"));
    TAF.stubFor(
        post(TOKEN_URL)
            .withRequestBody(
                equalTo(
                    "grant_type=client_credentials&client_id=bank-one&client_secret=secret-one"
                        + "&scope=antifraud"))
            .willReturn(okJson("{\"access_token\":\"taf-token\",\"expires_in\":10800}")));
    adapter =
        HttpServiceProxyFactory.builderFor(
                RestClientAdapter.create(Callers.gateway(port, "taf.use")))
            .build()
            .createClient(TafServiceClient.class);
  }

  static SendCodeRequest code(UUID requestId, CodeType type) {
    return new SendCodeRequest("+998901234567", requestId, "472915", type, Language.UZ);
  }

  @Test
  void aRegistrationCodeGoesToTafAndTheVerdictsComeBack() {
    TAF.stubFor(
        post(SMS)
            .withHeader("Authorization", equalTo("Bearer taf-token"))
            .withHeader("Accept-Language", equalTo("uz"))
            .withRequestBody(
                equalToJson(
                    "{\"phone\":\"998901234567\",\"requestId\":\""
                        + REQUEST
                        + "\",\"code\":\"472915\",\"templateId\":1}"))
            .willReturn(aResponse().withStatus(200)));
    TAF.stubFor(
        get(FRAUD)
            .willReturn(
                okJson(
                    """
                    {"requestId": "a1b2c3d4-e5f6-7890-abcd-1234567890ef", "phone": "998901234567",
                     "isActive": true, "codeUsable": true, "smsSent": true, "validStatus": true,
                     "verified": true, "deviceSimMatch": false, "noCloneDetected": true,
                     "createdDate": "2024-05-02T12:35:16.291Z"}
                    """)));
    TAF.stubFor(
        get(FRAUD + "/ai")
            .willReturn(
                okJson(
                    "{\"decision\":\"YELLOW\",\"mainReason\":\"SIM_DEVICE_MISMATCH\","
                        + "\"verdictReady\":true}")));

    assertThat(adapter.sendCode(code(REQUEST, CodeType.REGISTRATION)).getStatusCode().value())
        .isEqualTo(204);
    var basic = adapter.getBasicVerdict(REQUEST).getBody();
    assertThat(basic.getVerified()).isTrue();
    assertThat(basic.getDeviceSimMatch()).isFalse();
    var risk = adapter.getRiskVerdict(REQUEST).getBody();
    assertThat(risk.getDecision()).isEqualTo(Decision.YELLOW);
    assertThat(risk.getMainReason()).isEqualTo("SIM_DEVICE_MISMATCH");
    assertThat(risk.getVerdictReady()).isTrue();

    // One token for the three calls, in Valkey until 5 min before TAF's 3 h, encrypted (SAD TB-5).
    TAF.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_URL)));
    var stored = valkey.opsForValue().get("taf-adapter:token");
    assertThat(stored).isNotNull().doesNotContain("taf-token");
    assertThat(new String(Base64.getDecoder().decode(stored))).doesNotContain("taf-token");
    assertThat(valkey.getExpire("taf-adapter:token")).isBetween(10400L, 10500L);
  }

  @Test
  void aRiskVerdictThatIsNotReadyHasNoDecision() {
    TAF.stubFor(
        get(FRAUD + "/ai")
            .willReturn(okJson("{\"decision\":null,\"mainReason\":null,\"verdictReady\":false}")));

    var risk = adapter.getRiskVerdict(REQUEST).getBody();
    assertThat(risk.getVerdictReady()).isFalse();
    assertThat(risk.getDecision()).isNull();
    assertThat(risk.getMainReason()).isNull();
  }

  @Test
  void aLoginCodeCarriesTheDeviceModelAndNeedsIt() {
    TAF.stubFor(
        post(SMS)
            .withRequestBody(matchingJsonPath("$.templateId", equalTo("2")))
            .withRequestBody(matchingJsonPath("$.deviceName", equalTo("Samsung A36")))
            .willReturn(aResponse().withStatus(200)));

    assertProblem(() -> adapter.sendCode(code(REQUEST, CodeType.LOGIN)), 400, "VALIDATION_FAILED");
    TAF.verify(0, postRequestedFor(urlPathEqualTo(SMS)));

    adapter.sendCode(code(REQUEST, CodeType.LOGIN).deviceModel("Samsung A36"));
    TAF.verify(1, postRequestedFor(urlPathEqualTo(SMS)));
  }

  @Test
  void tafRefusalsAreTheContractsErrors() {
    refuse(110, 400, "Code already sent to given phone number.");
    assertSendProblem(409, "TAF_ADAPTER_CODE_ALREADY_SENT");
    refuse(25, 403, "Your account has been blocked.");
    assertSendProblem(403, "TAF_ADAPTER_NUMBER_BLOCKED");
    refuse(39, 403, "This action is forbidden because the service is disabled in my.gov.uz");
    var trusted = assertSendProblem(403, "TAF_ADAPTER_TRUSTED_BANK_REFUSED");
    assertThat(trusted.getResponseBodyAsString())
        .contains("{\"code\":\"403\",\"message\":\"39: This action is forbidden");
    refuse(115, 400, "Your device is not supported");
    assertSendProblem(422, "TAF_ADAPTER_DEVICE_NOT_SUPPORTED");
    refuse(113, 400, "Request id must be unique; it already exists.");
    assertSendProblem(502, "TAF_ADAPTER_TAF_CLIENT_ERROR");
    refuse(37, 500, "No static resource");
    assertSendProblem(503, "TAF_ADAPTER_SERVICE_UNAVAILABLE");
    TAF.stubFor(post(SMS).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));
    assertSendProblem(503, "TAF_ADAPTER_SERVICE_UNAVAILABLE");
    TAF.stubFor(
        get(FRAUD)
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withBody("{\"code\":12,\"message\":\"Request id not found\"}")));
    assertProblem(() -> adapter.getBasicVerdict(REQUEST), 404, "TAF_ADAPTER_NOT_FOUND");
  }

  @Test
  void anAnswerOfAnotherShapeIsAClientErrorNotAGuess() {
    TAF.stubFor(get(FRAUD).willReturn(okJson("{\"verified\":true}")));
    TAF.stubFor(
        get(FRAUD + "/ai").willReturn(okJson("{\"decision\":\"BLUE\",\"verdictReady\":true}")));

    assertProblem(() -> adapter.getBasicVerdict(REQUEST), 502, "TAF_ADAPTER_TAF_CLIENT_ERROR");
    assertProblem(() -> adapter.getRiskVerdict(REQUEST), 502, "TAF_ADAPTER_TAF_CLIENT_ERROR");
  }

  @Test
  void aFaceConfirmationAndThePhoneOwner() {
    TAF.stubFor(
        post(FACE)
            .withRequestBody(
                equalToJson("{\"pinfl\":\"30101003300128\",\"phone\":\"998901234567\"}"))
            .willReturn(aResponse().withStatus(200)));
    TAF.stubFor(
        post(FACE)
            .withRequestBody(matchingJsonPath("$.pinfl", equalTo("12345000000000")))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withBody("{\"code\":119,\"message\":\"Account not found\"}")));
    TAF.stubFor(
        get(urlPathEqualTo(OWNER))
            .withQueryParam("applicationId", equalTo("app-1"))
            .willReturn(okJson("{\"owner\":true}")));
    TAF.stubFor(
        get(urlPathEqualTo(OWNER))
            .withQueryParam("applicationId", equalTo("app-2"))
            .willReturn(okJson("{\"owner\":false}")));
    TAF.stubFor(
        get(urlPathEqualTo(OWNER))
            .withQueryParam("applicationId", equalTo("app-3"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody(
                        "{\"code\":119,\"message\":\" Account not found by applicationId.\"}")));

    assertThat(
            adapter
                .confirmFace(new ConfirmFaceRequest("30101003300128", "998901234567"))
                .getStatusCode()
                .value())
        .isEqualTo(204);
    assertProblem(
        () -> adapter.confirmFace(new ConfirmFaceRequest("12345000000000", "998901234567")),
        404,
        "TAF_ADAPTER_NOT_FOUND");
    assertThat(adapter.getPhoneOwner("app-1").getBody().getOwner()).isTrue();
    assertThat(adapter.getPhoneOwner("app-2").getBody().getOwner()).isFalse();
    assertProblem(() -> adapter.getPhoneOwner("app-3"), 404, "TAF_ADAPTER_NOT_FOUND");
  }

  @Test
  void aRefusedTokenIsRenewedOnceThenUnauthorized() {
    TAF.stubFor(
        get(FRAUD)
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withBody("{\"code\":23,\"message\":\"Authorization header is missing\"}")));

    assertProblem(() -> adapter.getBasicVerdict(REQUEST), 502, "TAF_ADAPTER_UNAUTHORIZED");
    TAF.verify(2, postRequestedFor(urlPathEqualTo(TOKEN_URL)));
    TAF.verify(2, getRequestedFor(urlPathEqualTo(FRAUD)));
  }

  @Test
  void aRefusedCredentialIsNoOutageForTheBreaker() {
    TAF.stubFor(post(TOKEN_URL).willReturn(aResponse().withStatus(401)));
    for (var i = 0; i < 6; i++) {
      assertProblem(() -> adapter.getBasicVerdict(REQUEST), 502, "TAF_ADAPTER_UNAUTHORIZED");
    }
    TAF.verify(0, getRequestedFor(urlPathEqualTo(FRAUD)));
    assertThat(adapter.getHealth().getBody().getStatus()).isEqualTo(Health.StatusEnum.DOWN);
    TAF.stubFor(
        post(TOKEN_URL)
            .willReturn(okJson("{\"access_token\":\"taf-token\",\"expires_in\":10800}")));
    TAF.stubFor(get(HEALTH).willReturn(okJson("{\"status\":\"UP\"}")));

    // Seven refusals in a row, and the breaker (5 failures) is still closed: TAF is reached.
    assertThat(adapter.getHealth().getBody().getStatus()).isEqualTo(Health.StatusEnum.UP);
  }

  @Test
  void theHealthIsUpOnlyWhenTheAgentSaysUpAndTheBreakerOpensAfterFiveFailures() {
    TAF.stubFor(
        get(HEALTH)
            .withHeader("Authorization", equalTo("Bearer taf-token"))
            .willReturn(okJson("{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}")));
    assertThat(adapter.getHealth().getBody().getStatus()).isEqualTo(Health.StatusEnum.UP);

    TAF.stubFor(
        get(HEALTH)
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withBody("{\"status\":\"DOWN\",\"details\":{\"db\":{\"status\":\"DOWN\"}}}")));
    for (var i = 0; i < 5; i++) {
      assertThat(adapter.getHealth().getBody().getStatus()).isEqualTo(Health.StatusEnum.DOWN);
    }
    TAF.stubFor(get(HEALTH).willReturn(okJson("{\"status\":\"UP\"}")));

    // Open: no call reaches TAF until the probe after 30 s.
    assertThat(adapter.getHealth().getBody().getStatus()).isEqualTo(Health.StatusEnum.DOWN);
    assertProblem(
        () -> adapter.sendCode(code(REQUEST, CodeType.REGISTRATION)),
        503,
        "TAF_ADAPTER_SERVICE_UNAVAILABLE");
    TAF.verify(6, getRequestedFor(urlPathEqualTo(HEALTH)));
    TAF.verify(0, postRequestedFor(urlPathEqualTo(SMS)));
  }

  @Test
  void theCallersMistakesNeverReachTaf() {
    assertProblem(
        () ->
            adapter.sendCode(
                new SendCodeRequest(
                    "901234567", REQUEST, "472915", CodeType.REGISTRATION, Language.RU)),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        () -> adapter.confirmFace(new ConfirmFaceRequest("123", "998901234567")),
        400,
        "VALIDATION_FAILED");
    TAF.verify(0, postRequestedFor(urlPathEqualTo(SMS)));
    TAF.verify(0, postRequestedFor(urlPathEqualTo(FACE)));
  }

  @Test
  void aCallerWithoutTheRoleIsRefused() {
    var stranger =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(Callers.gateway(port)))
            .build()
            .createClient(TafServiceClient.class);
    assertThatThrownBy(() -> stranger.getBasicVerdict(REQUEST))
        .isInstanceOfSatisfying(
            RestClientResponseException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
  }

  @Test
  void aTokenWrittenWithAnotherKeyIsReplaced() {
    valkey.opsForValue().set("taf-adapter:token", "c29tZXRoaW5nIGVsc2UgZW50aXJlbHk=");
    TAF.stubFor(get(HEALTH).willReturn(okJson("{\"status\":\"UP\"}")));

    adapter.getHealth();
    TAF.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_URL)));
  }

  @Test
  void tafIsTrustedOnlyThroughItsPinnedCa() {
    var pinned =
        HttpClient.newBuilder().sslContext(sslBundles.getBundle("taf").createSslContext()).build();
    assertThatThrownBy(
            () ->
                pinned.send(
                    HttpRequest.newBuilder(
                            URI.create("https://localhost:" + FALSE_TAF.httpsPort() + "/"))
                        .build(),
                    HttpResponse.BodyHandlers.discarding()))
        .isInstanceOf(SSLHandshakeException.class);
  }

  void refuse(int tafCode, int status, String message) {
    TAF.stubFor(
        post(SMS)
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withBody("{\"code\":" + tafCode + ",\"message\":\"" + message + "\"}")));
  }

  RestClientResponseException assertSendProblem(int status, String code) {
    return assertProblem(
        () -> adapter.sendCode(code(REQUEST, CodeType.REGISTRATION)), status, code);
  }

  static RestClientResponseException assertProblem(Runnable call, int status, String code) {
    var problem = new RestClientResponseException[1];
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            RestClientResponseException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(status);
              assertThat(e.getResponseBodyAsString()).contains("\"code\":\"" + code + "\"");
              problem[0] = e;
            });
    return problem[0];
  }
}
