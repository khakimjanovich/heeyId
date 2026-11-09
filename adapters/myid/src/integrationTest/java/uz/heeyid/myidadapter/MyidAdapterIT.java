package uz.heeyid.myidadapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.testcontainers.containers.GenericContainer;
import uz.khakimjanovich.contracts.myidadapter.api.client.MyidCompareServiceClient;
import uz.khakimjanovich.contracts.myidadapter.api.client.MyidInplaceServiceClient;
import uz.khakimjanovich.contracts.myidadapter.api.client.MyidOAuthServiceClient;
import uz.khakimjanovich.contracts.myidadapter.api.client.MyidSdkServiceClient;
import uz.khakimjanovich.contracts.myidadapter.api.client.MyidValidationServiceClient;
import uz.khakimjanovich.contracts.myidadapter.api.client.MyidWebServiceClient;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthenticationMethod;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CreateCompareTaskRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.DataScope;
import uz.khakimjanovich.contracts.myidadapter.api.model.ExchangeAuthorizationCodeRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetAddressRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetResultRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.ImageIssue;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenFullProofingSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.ReasonKey;
import uz.khakimjanovich.contracts.myidadapter.api.model.SessionState;
import uz.khakimjanovich.contracts.myidadapter.api.model.UploadInplaceMediaRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerificationStatus;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.WebJob;

/**
 * The adapter as heeyID's gateways see it: its generated client over mutual TLS, MyID played by
 * WireMock with a 1 second read timeout.
 */
// The random management port is read before the dynamic properties exist.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "MYID_ADAPTER_MANAGEMENT_PORT=0")
class MyidAdapterIT {

  static final String TENANT = "tenant-one";
  static final UUID SESSION = UUID.fromString("c328025e-e00c-4720-aad0-0c8cd9722bec");
  static final UUID JOB = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

  /** MyID's own CA: the adapter trusts MyID only through it (MYID_TRUSTED_CAS). */
  static final ThrowawayCa MYID_CA = new ThrowawayCa(Callers.directory(), "myid");

  static final WireMockServer MYID = new WireMockServer(tls(MYID_CA.server("myid")));

  /** A MyID host whose certificate comes from another CA: the adapter must refuse it. */
  static final WireMockServer FALSE_MYID =
      new WireMockServer(tls(Callers.HEEYID_CA.server("false-myid")));

  static final String TOKEN_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  /** Valkey with an ACL user, as heeyid-infra runs it. */
  static final GenericContainer<?> VALKEY =
      new GenericContainer<>("valkey/valkey:8.1-alpine")
          .withCommand(
              "valkey-server",
              "--user",
              "default",
              "off",
              "--user",
              "myid-adapter",
              "on",
              ">valkey-it",
              "~*",
              "+@all")
          .withExposedPorts(6379);

  static {
    MYID.start();
    FALSE_MYID.start();
    VALKEY.start();
    // Spring imports the tenants file before dynamic properties exist: a system property it is.
    try {
      var tenants =
          Files.writeString(
              Callers.directory().resolve("tenants.yaml"),
              """
              myid:
                tenants:
                  tenant-one:
                    client-id: client-one
                    client-secret: secret-one
                    username: login-one
                    password: password-one
                  tenant-two:
                    client-id: client-two
                    client-secret: secret-two
              """);
      System.setProperty("MYID_ADAPTER_TENANTS_FILE", tenants.toString());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void myid(DynamicPropertyRegistry registry) {
    Callers.register(registry);
    registry.add("MYID_BASE_URL", MyidAdapterIT::myidUrl);
    registry.add("MYID_MEDIA_URL", () -> myidUrl() + "/media");
    registry.add("MYID_TRUSTED_CAS", () -> "file:" + MYID_CA.certificate());
    registry.add("MYID_ADAPTER_TOKEN_KEY", () -> TOKEN_KEY);
    registry.add("MYID_CONNECT_TIMEOUT", () -> "1s");
    registry.add("MYID_READ_TIMEOUT", () -> "1s");
    registry.add("MYID_BREAKER_FAILURES", () -> "5");
    registry.add("MYID_BREAKER_PROBE_AFTER", () -> "30s");
    registry.add("VALKEY_HOST", VALKEY::getHost);
    registry.add("VALKEY_PORT", () -> VALKEY.getMappedPort(6379));
    registry.add("VALKEY_USERNAME", () -> "myid-adapter");
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

  static String myidUrl() {
    return "https://localhost:" + MYID.httpsPort();
  }

  @LocalServerPort int port;

  /** Every operation of the adapter's contract, as one client: the generated interfaces. */
  @HttpExchange
  interface MyidAdapterClient
      extends MyidSdkServiceClient,
          MyidInplaceServiceClient,
          MyidOAuthServiceClient,
          MyidWebServiceClient,
          MyidCompareServiceClient,
          MyidValidationServiceClient {}

  MyidAdapterClient adapter;

  @Autowired StringRedisTemplate valkey;
  @Autowired SslBundles sslBundles;
  @Autowired HealthContributorRegistry health;
  @Autowired ReactiveHealthContributorRegistry reactiveHealth;

  @BeforeEach
  void client() {
    MYID.resetAll();
    valkey.delete(valkey.keys("*"));
    MYID.stubFor(
        post("/api/v1/auth/clients/access-token")
            .withRequestBody(
                equalToJson("{\"client_id\":\"client-one\",\"client_secret\":\"secret-one\"}"))
            .willReturn(okJson("{\"access_token\":\"sdk-token\",\"expires_in\":3600}")));
    adapter =
        HttpServiceProxyFactory.builderFor(
                RestClientAdapter.create(Callers.gateway(port, "myid.use")))
            .build()
            .createClient(MyidAdapterClient.class);
  }

  @Test
  void aFullProofingOpensASessionAndGetsTheProfileWithTheCode() {
    MYID.stubFor(
        post("/api/v2/sdk/sessions")
            .withHeader("Authorization", equalTo("Bearer sdk-token"))
            .withRequestBody(
                equalToJson(
                    "{\"threshold\":0.9,\"pass_data\":\"AA1234567\",\"birth_date\":\"1990-05-15\"}"))
            .willReturn(
                okJson("{\"session_id\":\"" + SESSION + "\"}").withHeader("X-Request-ID", "r-1")));
    MYID.stubFor(
        get(urlPathEqualTo("/api/v1/sdk/data"))
            .withQueryParam("code", equalTo("one-time"))
            .willReturn(
                okJson(
                    """
                    {"data": {"comparison_value": 0.92, "pass_data": "AA1234567",
                      "job_id": "550e8400-e29b-41d4-a716-446655440000",
                      "profile": {"common_data": {"first_name": "ALI", "last_name": "VALIEV",
                        "pinfl": "30101003300128", "birth_date": "1990-05-15", "sdk_hash": "h"},
                        "doc_data": {"pass_data": "AA1234567", "doc_type_id": "46"},
                        "contacts": {"phone": "998901234567"},
                        "address": {"permanent_address": "Tashkent", "temporary_address": null}}},
                     "reuid": {"expires_at": 1722412345, "value": "9b7e597e-893e-4e11-92cf-f4e7d4f923b1"}}
                    """)));

    var session =
        adapter
            .openFullProofingSession(
                TENANT,
                new OpenFullProofingSessionRequest(0.9)
                    .passData("AA1234567")
                    .birthDate(LocalDate.of(1990, 5, 15)))
            .getBody();
    assertThat(session.getRequestId()).isEqualTo("r-1");
    assertThat(session.getData().getSessionId()).isEqualTo(SESSION);

    var result = adapter.getResult(TENANT, new GetResultRequest("one-time")).getBody().getData();
    assertThat(result.getComparisonValue()).isEqualTo(0.92);
    assertThat(result.getJobId()).isEqualTo(JOB);
    assertThat(result.getProfile().getCommonData().getFirstName()).isEqualTo("ALI");
    assertThat(result.getProfile().getDocData().getDocTypeId()).isEqualTo("46");
    assertThat(result.getProfile().getContacts().getPhone()).isEqualTo("998901234567");
    assertThat(result.getProfile().getAddress().getPermanentAddress()).isEqualTo("Tashkent");
    assertThat(result.getReuid().getExpiresAt()).isEqualTo(1722412345L);
    // One token for both calls, in Valkey under the tenant's prefix until 5 min before expiry,
    // encrypted (SAD TB-5).
    MYID.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/auth/clients/access-token")));
    var stored = valkey.opsForValue().get("t:tenant-one:myid-adapter:token:client");
    assertThat(stored).isNotNull().doesNotContain("sdk-token");
    assertThat(new String(Base64.getDecoder().decode(stored))).doesNotContain("sdk-token");
    assertThat(valkey.getExpire("t:tenant-one:myid-adapter:token:client")).isBetween(3200L, 3300L);
  }

  @Test
  void theSessionStatusNamesEachAttemptsReason() {
    MYID.stubFor(
        get("/api/v1/sdk/sessions/" + SESSION)
            .willReturn(
                okJson(
                    """
                    {"code": null, "status": "closed", "attempts": [
                      {"job_id": "550e8400-e29b-41d4-a716-446655440000",
                       "timestamp": "2025-05-27T12:34:56Z", "reason": "grayscale", "reason_code": 23},
                      {"job_id": "550e8400-e29b-41d4-a716-446655440001",
                       "timestamp": "2025-05-27T12:35:56Z", "reason": null, "reason_code": null}]}
                    """)));
    MYID.stubFor(
        post("/api/v1/profile/address")
            .withRequestBody(equalToJson("{\"job_id\":\"" + JOB + "\"}"))
            .willReturn(
                okJson(
                    "{\"response_id\":\""
                        + SESSION
                        + "\",\"profile\":{\"address\":"
                        + "{\"permanent_address\":\"Tashkent\"}}}")));

    var status = adapter.getSessionStatus(TENANT, SESSION).getBody().getData();
    assertThat(status.getStatus()).isEqualTo(SessionState.CLOSED);
    assertThat(status.getCode()).isNull();
    assertThat(status.getAttempts().get(0).getReasonKey()).isEqualTo(ReasonKey.POOR_IMAGE);
    assertThat(status.getAttempts().get(0).getImageIssue()).isEqualTo(ImageIssue.GRAYSCALE);
    assertThat(status.getAttempts().get(1).getReasonKey()).isNull();

    var address = adapter.getAddressByJob(TENANT, new GetAddressRequest(JOB)).getBody().getData();
    assertThat(address.getResponseId()).isEqualTo(SESSION);
    assertThat(address.getAddress().getPermanentAddress()).isEqualTo("Tashkent");
  }

  @Test
  void myidRefusalsAreTheContractsErrors() {
    MYID.stubFor(
        get(urlPathEqualTo("/api/v1/sdk/data"))
            .withQueryParam("code", equalTo("used"))
            .willReturn(aResponse().withStatus(400).withBody("{\"detail\":\"Invalid code\"}")));
    MYID.stubFor(
        get(urlPathEqualTo("/api/v1/sdk/data"))
            .withQueryParam("code", equalTo("blocked"))
            .willReturn(aResponse().withStatus(429).withBody("{\"ttl\":3600}")));
    MYID.stubFor(
        get(urlPathEqualTo("/api/v1/sdk/data"))
            .withQueryParam("code", equalTo("down"))
            .willReturn(aResponse().withStatus(502).withBody("<html>bad gateway</html>")));
    MYID.stubFor(
        get(urlPathEqualTo("/api/v1/sdk/data"))
            .withQueryParam("code", equalTo("slow"))
            .willReturn(okJson("{}").withFixedDelay(2_000)));
    MYID.stubFor(
        get("/api/v1/sdk/sessions/" + SESSION)
            .willReturn(aResponse().withStatus(404).withBody("{\"detail\":\"Not found\"}")));

    assertProblem(
        () -> adapter.getResult(TENANT, new GetResultRequest("used")),
        400,
        "MYID_ADAPTER_CODE_NOT_VALID");
    assertProblem(
        () -> adapter.getResult(TENANT, new GetResultRequest("blocked")),
        429,
        "MYID_ADAPTER_USER_BLOCKED");
    assertProblem(
        () -> adapter.getResult(TENANT, new GetResultRequest("down")),
        503,
        "MYID_ADAPTER_SERVICE_UNAVAILABLE");
    assertProblem(
        () -> adapter.getResult(TENANT, new GetResultRequest("slow")),
        503,
        "MYID_ADAPTER_SERVICE_UNAVAILABLE");
    assertProblem(() -> adapter.getSessionStatus(TENANT, SESSION), 404, "MYID_ADAPTER_NOT_FOUND");
  }

  @Test
  void aRefusedTokenIsRenewedOnceThenUnauthorized() {
    MYID.stubFor(
        get("/api/v1/sdk/sessions/" + SESSION)
            .willReturn(
                aResponse().withStatus(403).withBody("{\"detail\":\"Could not validate\"}")));

    assertProblem(
        () -> adapter.getSessionStatus(TENANT, SESSION), 502, "MYID_ADAPTER_UNAUTHORIZED");
    MYID.verify(2, postRequestedFor(urlPathEqualTo("/api/v1/auth/clients/access-token")));
    MYID.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/sdk/sessions/" + SESSION)));
  }

  @Test
  void theCallersMistakesNeverReachMyid() {
    assertProblem(
        () ->
            adapter.openFullProofingSession(
                TENANT, new OpenFullProofingSessionRequest(0.9).passData("AA1234567")),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        () -> adapter.getResult("tenant-unknown", new GetResultRequest("x")),
        400,
        "VALIDATION_FAILED");
    MYID.verify(0, postRequestedFor(urlPathEqualTo("/api/v2/sdk/sessions")));
  }

  @Test
  void anInplacePhotoGoesAsAFileAndTheJobAnswers202WhileProcessing() {
    MYID.stubFor(
        post("/media/api/v1/inplace/upload")
            .withRequestBody(containing("name=\"photo_front\""))
            .willReturn(okJson("{\"media_id\":\"" + JOB + "\"}")));
    MYID.stubFor(
        get("/api/v1/inplace/jobs/" + JOB).willReturn(aResponse().withStatus(202).withBody("{}")));

    var png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    var media =
        adapter
            .uploadInplaceMedia(TENANT, new UploadInplaceMediaRequest(SESSION, png))
            .getBody()
            .getData();
    assertThat(media.getMediaId()).isEqualTo(JOB);
    assertThat(adapter.getInplaceJobResult(TENANT, JOB).getStatusCode().value()).isEqualTo(202);
  }

  @Test
  void anAuthorizationCodeGivesThePersonsData() {
    var payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"job_id\":\"job-77\"}".getBytes());
    MYID.stubFor(
        post("/api/v1/oauth2/access-token")
            .withRequestBody(containing("grant_type=authorization_code"))
            .withRequestBody(containing("code=qr-code"))
            .withRequestBody(containing("client_secret=secret-one"))
            .willReturn(
                okJson(
                    "{\"access_token\":\"h."
                        + payload
                        + ".s\",\"expires_in\":3600,"
                        + "\"scope\":\"common_data\",\"refresh_token\":\"refresh-1\"}")));
    MYID.stubFor(
        get("/api/v1/users/me")
            .withHeader("Authorization", equalTo("Bearer h." + payload + ".s"))
            .willReturn(
                okJson(
                    "{\"profile\":{\"common_data\":{\"first_name\":\"ALI\"},"
                        + "\"authentication_method\":\"strong\"}}")));

    var data =
        adapter
            .exchangeAuthorizationCode(TENANT, new ExchangeAuthorizationCodeRequest("qr-code"))
            .getBody()
            .getData();
    assertThat(data.getJobId()).isEqualTo("job-77");
    assertThat(data.getScope()).isEqualTo("common_data");
    assertThat(data.getAuthenticationMethod()).isEqualTo("strong");
    assertThat(data.getRefreshToken()).isEqualTo("refresh-1");
    assertThat(data.getProfile().getCommonData().getFirstName()).isEqualTo("ALI");

    var url =
        adapter
            .getAuthorizationUrl(
                TENANT,
                new AuthorizationUrlRequest(
                    Set.of(DataScope.COMMON_DATA), AuthenticationMethod.STRONG, "xyz"))
            .getBody()
            .getData()
            .getUrl();
    assertThat(url)
        .startsWith(myidUrl() + "/api/v1/oauth2/authorization?client_id=client-one")
        .contains("response_type=code", "scope=common_data", "method=strong", "state=xyz");
    var qr =
        adapter
            .getQrContent(
                TENANT,
                new QrContentRequest(AuthenticationMethod.STRONG)
                    .extraInfo(Map.of("branch_id", "tash00991")))
            .getBody()
            .getData()
            .getContent();
    assertThat(qr).isEqualTo("client_id=client-one&method=strong&branch_id=tash00991");
  }

  @Test
  void aPhotoComparisonUsesTheTenantsLogin() {
    MYID.stubFor(
        post("/api/v1/oauth2/access-token")
            .withRequestBody(containing("grant_type=password"))
            .withRequestBody(containing("username=login-one"))
            .willReturn(okJson("{\"access_token\":\"compare-token\",\"expires_in\":3600}")));
    MYID.stubFor(
        post("/api/v1/authentication/compare-photos-task")
            .withHeader("Authorization", equalTo("Bearer compare-token"))
            .withRequestBody(containing("data:image/jpeg;base64,"))
            .willReturn(okJson("{\"job_id\":\"job-9\"}")));
    MYID.stubFor(
        post("/api/v1/authentication/compare-photos-status")
            .willReturn(
                okJson("{\"comparison_value\":0.81,\"result_code\":1,\"result_note\":\"ok\"}")));

    var job =
        adapter
            .createCompareTask(
                TENANT, new CreateCompareTaskRequest(new byte[] {1, 2, 3}, true, true, false))
            .getBody()
            .getData();
    assertThat(job.getJobId()).isEqualTo("job-9");
    var result =
        adapter.getCompareResult(TENANT, new CompareResultRequest("job-9")).getBody().getData();
    assertThat(result.getReasonKey()).isEqualTo(ReasonKey.PASSED);
    assertThat(result.getComparisonValue()).isEqualTo(0.81);

    assertProblem(
        () -> adapter.getCompareResult("tenant-two", new CompareResultRequest("job-9")),
        502,
        "MYID_ADAPTER_UNAUTHORIZED");
  }

  @Test
  void aWebSessionsJobsAndABnplValidation() {
    MYID.stubFor(
        post("/api/v1/oauth2/access-token")
            .withRequestBody(containing("grant_type=client_credentials"))
            .willReturn(okJson("{\"access_token\":\"web-token\",\"expires_in\":3600}")));
    MYID.stubFor(
        post("/api/v1/web/sessions/" + SESSION + "/result")
            .withHeader("Authorization", equalTo("Bearer web-token"))
            .willReturn(
                okJson(
                    "{\"jobs\":[{\"job_id\":\""
                        + JOB
                        + "\",\"result_code\":21,"
                        + "\"result_note\":\"Face not fully shown\"}],\"auth_code\":null}")));
    MYID.stubFor(
        post("/api/v1/bnpl/verify")
            .withHeader("Authorization", absent())
            .withRequestBody(containing("bnpl_id=b-1"))
            .willReturn(
                okJson("{\"job_id\":\"j-1\",\"bnpl_id\":\"b-1\",\"status\":2,\"result\":false}")));

    var result = adapter.getWebSessionResult(TENANT, SESSION).getBody().getData();
    WebJob job = result.getJobs().get(0);
    assertThat(job.getImageIssue()).isEqualTo(ImageIssue.FACE_NOT_FULLY_SHOWN);
    assertThat(result.getAuthCode()).isNull();

    var verification =
        adapter
            .verifyIdentification(
                TENANT, new VerifyIdentificationRequest("j-1", "bank-1", "b-1", "30101003300128"))
            .getBody()
            .getData();
    assertThat(verification.getStatus()).isEqualTo(VerificationStatus.EXPIRED);
    assertThat(verification.getResult()).isFalse();
  }

  @Test
  void aCallerWithoutTheRoleIsRefused() {
    var stranger =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(Callers.gateway(port)))
            .build()
            .createClient(MyidAdapterClient.class);
    assertThatThrownBy(() -> stranger.getSessionStatus(TENANT, SESSION))
        .isInstanceOfSatisfying(
            RestClientResponseException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
  }

  @Test
  void aTokenWrittenWithAnotherKeyIsReplaced() {
    valkey
        .opsForValue()
        .set("t:tenant-one:myid-adapter:token:client", "c29tZXRoaW5nIGVsc2UgZW50aXJlbHk=");
    MYID.stubFor(
        get("/api/v1/sdk/sessions/" + SESSION)
            .willReturn(okJson("{\"status\": \"in_progress\", \"attempts\": []}")));

    adapter.getSessionStatus(TENANT, SESSION);
    MYID.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/auth/clients/access-token")));
  }

  @Test
  void myidIsTrustedOnlyThroughItsPinnedCa() {
    var pinned =
        HttpClient.newBuilder().sslContext(sslBundles.getBundle("myid").createSslContext()).build();
    assertThatThrownBy(
            () ->
                pinned.send(
                    HttpRequest.newBuilder(
                            URI.create("https://localhost:" + FALSE_MYID.httpsPort() + "/"))
                        .build(),
                    HttpResponse.BodyHandlers.discarding()))
        .isInstanceOf(SSLHandshakeException.class);
  }

  @Test
  void valkeyIsPartOfTheHealth() {
    assertThat(
            health.getContributor("redis") != null
                || reactiveHealth.getContributor("redis") != null)
        .as("a Valkey health contributor")
        .isTrue();
  }

  static void assertProblem(Runnable call, int status, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            RestClientResponseException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(status);
              assertThat(e.getResponseBodyAsString()).contains("\"code\":\"" + code + "\"");
            });
  }
}
