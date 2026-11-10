package uz.heeyid.pushadapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import uz.khakimjanovich.contracts.pushadapter.api.client.PushServiceClient;
import uz.khakimjanovich.contracts.pushadapter.api.model.MessageType;
import uz.khakimjanovich.contracts.pushadapter.api.model.SendMessageRequest;

/**
 * The adapter as heeyID's workers see it: its generated client over mutual TLS. FCM HTTP v1 and
 * Google's token service are played by WireMock (the FCM fake: Firebase has no FCM emulator). The
 * real Firebase Admin SDK sends; only its connections go to WireMock instead of fcm.googleapis.com.
 */
// The random management port is read before the dynamic properties exist.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "PUSH_ADAPTER_MANAGEMENT_PORT=0")
@Import(PushAdapterIT.FakeFcm.class)
class PushAdapterIT {

  static final String PROJECT = "heeyid-it";
  static final String SEND = "/v1/projects/" + PROJECT + "/messages:send";
  static final String TOKEN = "fcm-registration-token-of-the-device";

  /** The FCM fake, also Google's token service. */
  static final WireMockServer FCM = new WireMockServer(options().dynamicPort());

  static {
    FCM.start();
  }

  /** The SDK's connections to Google go to the fake. */
  @TestConfiguration
  static class FakeFcm {
    @Bean
    @Primary
    HttpTransport fakeFcmTransport() {
      return new NetHttpTransport.Builder()
          .setConnectionFactory(
              url ->
                  (HttpURLConnection)
                      URI.create(
                              url.toString().replace("https://fcm.googleapis.com", FCM.baseUrl()))
                          .toURL()
                          .openConnection())
          .build();
    }
  }

  @DynamicPropertySource
  static void push(DynamicPropertyRegistry registry) {
    Callers.register(registry);
    registry.add("FCM_PROJECT_ID", () -> PROJECT);
    registry.add("FCM_CREDENTIALS_FILE", PushAdapterIT::serviceAccount);
    registry.add("FCM_CONNECT_TIMEOUT", () -> "1s");
    registry.add("FCM_READ_TIMEOUT", () -> "1s");
    registry.add("FCM_SEND_TIMEOUT", () -> "3s");
    // High, so the failures of one test never open the breaker for the next (FcmBreakerTest).
    registry.add("PUSH_BREAKER_FAILURES", () -> "1000");
    registry.add("PUSH_BREAKER_PROBE_AFTER", () -> "30s");
    registry.add("TRACING_SAMPLING", () -> "0.0"); // no collector here: nothing is traced
    registry.add("OTLP_TRACES_ENDPOINT", () -> "http://otlp.not-started.invalid/v1/traces");
    registry.add("OTLP_METRICS_ENDPOINT", () -> "http://otlp.not-started.invalid/v1/metrics");
    registry.add("OTLP_LOGS_ENDPOINT", () -> "http://otlp.not-started.invalid/v1/logs");
  }

  /** A service-account JSON as the Vault agent writes it, with its token service on the fake. */
  static String serviceAccount() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var key =
          Base64.getMimeEncoder(64, "\n".getBytes())
              .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
      var pem = "-----BEGIN PRIVATE KEY-----\n" + key + "\n-----END PRIVATE KEY-----\n";
      var json =
          """
          {"type": "service_account", "project_id": "%s", "private_key_id": "it-key",
           "private_key": "%s", "client_email": "push@%s.iam.gserviceaccount.com",
           "client_id": "1", "token_uri": "%s/token"}
          """
              .formatted(PROJECT, pem.replace("\n", "\\n"), PROJECT, FCM.baseUrl());
      Path file = Files.createTempFile(Callers.directory(), "fcm", ".json");
      Files.writeString(file, json);
      return file.toString();
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @LocalServerPort int port;

  PushServiceClient adapter;

  @BeforeEach
  void client() {
    FCM.resetAll();
    FCM.stubFor(
        post("/token")
            .willReturn(
                okJson(
                    "{\"access_token\": \"it-access-token\", \"expires_in\": 3600,"
                        + " \"token_type\": \"Bearer\"}")));
    adapter = client("push.use");
  }

  PushServiceClient client(String... roles) {
    return HttpServiceProxyFactory.builderFor(
            RestClientAdapter.create(Callers.gateway(port, roles)))
        .build()
        .createClient(PushServiceClient.class);
  }

  static SendMessageRequest approval() {
    return new SendMessageRequest(TOKEN, MessageType.APPROVAL_REQUEST, "nd_8f3k2m9x", 300);
  }

  /** FCM's error body (HTTP v1: google.rpc.Status with an FcmError detail). */
  static void fcmRefuses(int status, String rpcStatus, String fcmErrorCode) {
    FCM.stubFor(
        post(SEND)
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"error": {"code": %d, "message": "refused", "status": "%s",
                          "details": [{"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError",
                                       "errorCode": "%s"}]}}
                        """
                            .formatted(status, rpcStatus, fcmErrorCode))));
  }

  @Test
  void anApprovalRequestGoesToFcmWithTheTypeAndTheReferenceOnly() {
    FCM.stubFor(post(SEND).willReturn(okJson("{\"name\": \"projects/heeyid-it/messages/m-1\"}")));

    var sent = adapter.sendMessage(approval()).getBody();

    assertThat(sent.getMessageId()).isEqualTo("projects/heeyid-it/messages/m-1");
    FCM.verify(
        postRequestedFor(urlEqualTo(SEND))
            .withHeader("Authorization", equalTo("Bearer it-access-token"))
            // No notification block, no text: Android shows nothing by itself.
            .withRequestBody(notContaining("\"notification\""))
            .withRequestBody(
                equalToJson(
                    """
                    {"message": {
                      "token": "%s",
                      "data": {"type": "APPROVAL_REQUEST", "reference": "nd_8f3k2m9x"},
                      "android": {"priority": "high", "ttl": "300s", "collapse_key": "nd_8f3k2m9x"},
                      "apns": {
                        "headers": {"apns-priority": "10", "apns-collapse-id": "nd_8f3k2m9x",
                                    "apns-expiration": "${json-unit.any-string}"},
                        "payload": {"aps": {"alert": {"loc-key": "APPROVAL_REQUEST"},
                                            "mutable-content": 1}}}}}
                    """
                        .formatted(TOKEN))));
  }

  @Test
  void anUnregisteredTokenIsNotValidAndFinal() {
    fcmRefuses(404, "NOT_FOUND", "UNREGISTERED");

    var problem =
        assertProblem(() -> adapter.sendMessage(approval()), 422, "PUSH_ADAPTER_TOKEN_NOT_VALID");

    assertThat(problem.getResponseBodyAsString()).contains("UNREGISTERED").doesNotContain(TOKEN);
    FCM.verify(1, postRequestedFor(urlEqualTo(SEND)));
  }

  @Test
  void anInvalidArgumentIsTheToken() {
    fcmRefuses(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT");

    assertProblem(() -> adapter.sendMessage(approval()), 422, "PUSH_ADAPTER_TOKEN_NOT_VALID");
  }

  @Test
  void aQuotaExceededIsRateLimitedWithFcmsRetryAfter() {
    FCM.stubFor(
        post(SEND)
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Retry-After", "30")
                    .withBody(
                        """
                        {"error": {"code": 429, "message": "quota", "status": "RESOURCE_EXHAUSTED",
                          "details": [{"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError",
                                       "errorCode": "QUOTA_EXCEEDED"}]}}
                        """)));

    var problem =
        assertProblem(() -> adapter.sendMessage(approval()), 429, "PUSH_ADAPTER_RATE_LIMITED");

    assertThat(problem.getResponseHeaders().getFirst("Retry-After")).isEqualTo("30");
  }

  @Test
  void anUnavailableFcmIsServiceUnavailable() {
    fcmRefuses(503, "UNAVAILABLE", "UNAVAILABLE");

    assertProblem(() -> adapter.sendMessage(approval()), 503, "PUSH_ADAPTER_SERVICE_UNAVAILABLE");
  }

  @Test
  void noAnswerInTimeIsServiceUnavailable() {
    FCM.stubFor(
        post(SEND)
            .willReturn(
                okJson("{\"name\": \"projects/heeyid-it/messages/late\"}").withFixedDelay(4000)));

    assertProblem(() -> adapter.sendMessage(approval()), 503, "PUSH_ADAPTER_SERVICE_UNAVAILABLE");
  }

  @Test
  void aBadReferenceIsRefusedBeforeFcm() {
    var bad = new SendMessageRequest(TOKEN, MessageType.NEW_SESSION, "has space", 60);

    assertProblem(() -> adapter.sendMessage(bad), 400, "VALIDATION_FAILED");
    FCM.verify(0, postRequestedFor(urlEqualTo(SEND)));
  }

  @Test
  void onlyACallerWithTheRoleSends() {
    assertProblem(() -> client("sms.use").sendMessage(approval()), 403, "FORBIDDEN");
    FCM.verify(0, postRequestedFor(urlEqualTo(SEND)));
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
