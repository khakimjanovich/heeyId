package uz.heeyid.smsadapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.testcontainers.containers.GenericContainer;
import uz.heeyid.smsadapter.vendor.OutgoingSms;
import uz.heeyid.smsadapter.vendor.SmsVendor;
import uz.heeyid.smsadapter.vendor.VendorOutcome;
import uz.khakimjanovich.contracts.smsadapter.api.client.SmsServiceClient;
import uz.khakimjanovich.contracts.smsadapter.api.model.MessageStatus;
import uz.khakimjanovich.contracts.smsadapter.api.model.MessageType;
import uz.khakimjanovich.contracts.smsadapter.api.model.SendMessageRequest;

/**
 * The adapter as heeyID's gateways see it: its generated client over mutual TLS. SMS Xabar is
 * played by WireMock (the simulator: the answers of the SMS-Broker API document), with a 1 second
 * read timeout; a second vendor "backup", a test bean, is the failover.
 */
// The random management port is read before the dynamic properties exist.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "SMS_ADAPTER_MANAGEMENT_PORT=0")
@Import(SmsAdapterIT.Backup.class)
class SmsAdapterIT {

  static final String SECRET = "xabar-callback-secret-0123456789abcdef";
  static final String PHONE = "998901234567";
  static final String OTP_TEXT = "Kod: 123456. Hech kimga aytmang.";

  /** The Xabar simulator. */
  static final WireMockServer XABAR = new WireMockServer(options().dynamicPort());

  static final int CALLBACK_PORT = freePort();

  /**
   * Valkey with heeyid-infra's ACL: the adapter's user reaches only keys sms-adapter:* and only the
   * commands it sends. The user it-admin reads and clears for the tests.
   */
  static final GenericContainer<?> VALKEY =
      new GenericContainer<>("valkey/valkey:8.1-alpine")
          .withCommand(
              "valkey-server",
              "--user",
              "default",
              "off",
              "--user",
              "sms-adapter",
              "on",
              ">valkey-it",
              "resetkeys",
              "~sms-adapter:*",
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
    XABAR.start();
    VALKEY.start();
  }

  /** The test's second vendor: what it answers, and what it was given. */
  static final AtomicReference<VendorOutcome> BACKUP_ANSWER = new AtomicReference<>();

  static final List<OutgoingSms> BACKUP_SENT = new CopyOnWriteArrayList<>();

  /** A second vendor module, as a bank's second vendor would be: one SmsVendor bean. */
  @TestConfiguration
  static class Backup {
    @Bean
    SmsVendor backupVendor() {
      return new SmsVendor() {
        @Override
        public String name() {
          return "backup";
        }

        @Override
        public Duration longestCall() {
          return Duration.ofSeconds(1);
        }

        @Override
        public VendorOutcome send(OutgoingSms sms) {
          BACKUP_SENT.add(sms);
          return BACKUP_ANSWER.get();
        }
      };
    }
  }

  @DynamicPropertySource
  static void sms(DynamicPropertyRegistry registry) {
    Callers.register(registry);
    registry.add("SMS_ADAPTER_CALLBACK_PORT", () -> CALLBACK_PORT);
    registry.add("SMS_ACTIVE_VENDOR", () -> "xabar");
    registry.add("SMS_FAILOVER_VENDORS", () -> "backup");
    registry.add("SMS_MESSAGE_ID_PREFIX", () -> "hid");
    registry.add("SMS_RETENTION", () -> "7d");
    // High, so the failures of one test never open a breaker for the next (VendorBreakerTest).
    registry.add("SMS_BREAKER_FAILURES", () -> "1000");
    registry.add("SMS_BREAKER_PROBE_AFTER", () -> "30s");
    registry.add("XABAR_BASE_URL", () -> XABAR.baseUrl() + "/broker-api");
    registry.add("XABAR_USERNAME", () -> "bank-login");
    registry.add("XABAR_PASSWORD", () -> "bank-password");
    registry.add("XABAR_ORIGINATOR", () -> "BANK");
    registry.add("XABAR_CONNECT_TIMEOUT", () -> "1s");
    registry.add("XABAR_READ_TIMEOUT", () -> "1s");
    registry.add("XABAR_CALLBACK_SECRET", () -> SECRET);
    registry.add("VALKEY_HOST", VALKEY::getHost);
    registry.add("VALKEY_PORT", () -> VALKEY.getMappedPort(6379));
    registry.add("VALKEY_USERNAME", () -> "sms-adapter");
    registry.add("VALKEY_PASSWORD", () -> "valkey-it");
    registry.add("TRACING_SAMPLING", () -> "0.0"); // no collector here: nothing is traced
    registry.add("OTLP_TRACES_ENDPOINT", () -> "http://otlp.not-started.invalid/v1/traces");
    registry.add("OTLP_METRICS_ENDPOINT", () -> "http://otlp.not-started.invalid/v1/metrics");
    registry.add("OTLP_LOGS_ENDPOINT", () -> "http://otlp.not-started.invalid/v1/logs");
  }

  @LocalServerPort int port;

  SmsServiceClient adapter;

  /** Valkey as it-admin: the adapter's own user may not list keys. */
  StringRedisTemplate valkey;

  @BeforeEach
  void client() {
    XABAR.resetAll();
    BACKUP_SENT.clear();
    BACKUP_ANSWER.set(VendorOutcome.accepted());
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
    adapter =
        HttpServiceProxyFactory.builderFor(
                RestClientAdapter.create(Callers.gateway(port, "sms.use")))
            .build()
            .createClient(SmsServiceClient.class);
  }

  /** Xabar's answer to a good send (section 2.1.3). */
  static void xabarTakes() {
    XABAR.stubFor(
        post("/broker-api/send")
            .willReturn(aResponse().withStatus(200).withBody("Request is received")));
  }

  static SendMessageRequest otp(String key) {
    return new SendMessageRequest(key, PHONE, OTP_TEXT, MessageType.OTP, 120);
  }

  @Test
  void anOtpGoesToXabarAsTheDocumentSays() {
    xabarTakes();

    var sent = adapter.sendMessage(otp("k-1")).getBody();

    assertThat(sent.getStatus()).isEqualTo(MessageStatus.ACCEPTED);
    assertThat(sent.getVendor()).isEqualTo("xabar");
    assertThat(sent.getType()).isEqualTo(MessageType.OTP);
    assertThat(sent.getMessageId()).hasSize(20).startsWith("hid").matches("^[a-z0-9]+$");
    XABAR.verify(
        postRequestedFor(urlEqualTo("/broker-api/send"))
            .withBasicAuth(new BasicCredentials("bank-login", "bank-password"))
            .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
            .withRequestBody(
                equalToJson(
                    """
                    {"messages": [{"recipient": "998901234567", "message-id": "%s",
                      "priority": "realtime",
                      "sms": {"originator": "BANK", "ttl": 120, "content": {"text": "%s"}}}]}
                    """
                        .formatted(sent.getMessageId(), OTP_TEXT))));
    assertThat(adapter.getMessage(sent.getMessageId()).getBody().getStatus())
        .isEqualTo(MessageStatus.ACCEPTED);
    assertThat(BACKUP_SENT).isEmpty();
    // Valkey keeps no text and no phone number.
    for (var key : valkey.keys("*")) {
      assertThat(valkey.opsForValue().get(key)).doesNotContain("123456").doesNotContain(PHONE);
    }
  }

  @Test
  void aSecurityNoticeGoesAsHighPriority() {
    xabarTakes();

    adapter.sendMessage(
        new SendMessageRequest(
            "k-n", PHONE, "Yangi qurilmadan kirildi.", MessageType.SECURITY_NOTICE, 3600));

    XABAR.verify(
        postRequestedFor(urlEqualTo("/broker-api/send"))
            .withRequestBody(matchingJsonPath("$.messages[0].priority", equalTo("high")))
            .withRequestBody(matchingJsonPath("$.messages[0].sms.ttl", equalTo("3600"))));
  }

  @Test
  void theSameKeySendsOnce() {
    xabarTakes();

    var first = adapter.sendMessage(otp("k-2")).getBody();
    var again = adapter.sendMessage(otp("k-2")).getBody();

    assertThat(again.getMessageId()).isEqualTo(first.getMessageId());
    XABAR.verify(1, postRequestedFor(urlEqualTo("/broker-api/send")));
    assertProblem(
        () ->
            adapter.sendMessage(
                new SendMessageRequest("k-2", PHONE, "another text", MessageType.OTP, 120)),
        409,
        "SMS_ADAPTER_KEY_CONFLICT");
  }

  @Test
  void aRefusedMessageGoesToNoOtherVendorAndFreesTheKey() {
    XABAR.stubFor(
        post("/broker-api/send")
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody(
                        "{\"error_code\":\"406\",\"error_description\":\"Invalid content\"}")));

    var refused =
        assertProblem(() -> adapter.sendMessage(otp("k-3")), 502, "SMS_ADAPTER_MESSAGE_REFUSED");
    assertThat(refused.getResponseBodyAsString())
        .contains("{\"code\":\"406\",\"message\":\"Invalid content\"}");
    assertThat(BACKUP_SENT).isEmpty();

    xabarTakes();
    assertThat(adapter.sendMessage(otp("k-3")).getBody().getStatus())
        .isEqualTo(MessageStatus.ACCEPTED);
  }

  @Test
  void whenXabarIsDownTheBackupSends() {
    XABAR.stubFor(post("/broker-api/send").willReturn(aResponse().withStatus(503)));

    var sent = adapter.sendMessage(otp("k-4")).getBody();

    assertThat(sent.getVendor()).isEqualTo("backup");
    assertThat(sent.getStatus()).isEqualTo(MessageStatus.ACCEPTED);
    assertThat(BACKUP_SENT)
        .singleElement()
        .satisfies(
            sms -> {
              assertThat(sms.messageId()).isEqualTo(sent.getMessageId());
              assertThat(sms.text()).isEqualTo(OTP_TEXT);
            });
  }

  @Test
  void xabarsOwnFaultsGoToTheBackup() {
    for (var code : List.of("100", "102")) {
      XABAR.stubFor(
          post("/broker-api/send")
              .willReturn(
                  aResponse()
                      .withStatus(400)
                      .withBody(
                          "{\"error_code\":\"" + code + "\",\"error_description\":\"fault\"}")));
      assertThat(adapter.sendMessage(otp("k-fault-" + code)).getBody().getVendor())
          .isEqualTo("backup");
    }
    XABAR.stubFor(post("/broker-api/send").willReturn(aResponse().withStatus(401)));
    assertThat(adapter.sendMessage(otp("k-login")).getBody().getVendor()).isEqualTo("backup");
  }

  @Test
  void noAnswerInTimeIsUnknownAndNoOtherVendorSends() {
    XABAR.stubFor(
        post("/broker-api/send")
            .willReturn(
                aResponse().withStatus(200).withBody("Request is received").withFixedDelay(2_000)));

    var sent = adapter.sendMessage(otp("k-5")).getBody();

    assertThat(sent.getStatus()).isEqualTo(MessageStatus.UNKNOWN);
    assertThat(sent.getVendor()).isEqualTo("xabar");
    assertThat(BACKUP_SENT).isEmpty();
  }

  @Test
  void whenEveryVendorIsDownNothingIsSentAndTheKeyIsFree() {
    XABAR.stubFor(post("/broker-api/send").willReturn(aResponse().withStatus(500)));
    BACKUP_ANSWER.set(VendorOutcome.unavailable("503", "down"));

    var down =
        assertProblem(
            () -> adapter.sendMessage(otp("k-6")), 503, "SMS_ADAPTER_SERVICE_UNAVAILABLE");
    assertThat(down.getResponseBodyAsString()).contains("xabar 500").contains("backup 503");

    BACKUP_ANSWER.set(VendorOutcome.accepted());
    assertThat(adapter.sendMessage(otp("k-6")).getBody().getVendor()).isEqualTo("backup");
  }

  @Test
  void xabarsStatusPushSetsTheStatus() throws Exception {
    xabarTakes();
    var sent = adapter.sendMessage(otp("k-7")).getBody();

    var answer =
        callback(
            "/delivery-reports/xabar/" + SECRET + "/status",
            """
            {"messages": [{"message-id": "%s", "channel": "sms", "status": "delivered",
              "status-date": "2026-11-10T10:15:30Z", "description": "", "operator": "ucell",
              "message-parts": "1"},
             {"message-id": "hidunknown0000000000", "channel": "sms", "status": "delivered"}]}
            """
                .formatted(sent.getMessageId()));

    assertThat(answer.statusCode()).isEqualTo(200);
    var status = adapter.getMessage(sent.getMessageId()).getBody();
    assertThat(status.getStatus()).isEqualTo(MessageStatus.DELIVERED);
    assertThat(status.getVendorStatus()).isEqualTo("delivered");
    assertThat(status.getParts()).isEqualTo(1);
    assertThat(status.getStatusAt()).isEqualTo(OffsetDateTime.parse("2026-11-10T10:15:30Z"));

    callback(
        "/delivery-reports/xabar/" + SECRET + "/status",
        "{\"messages\":[{\"message-id\":\"" + sent.getMessageId() + "\",\"status\":\"Queued\"}]}");
    var later = adapter.getMessage(sent.getMessageId()).getBody();
    assertThat(later.getStatus()).isEqualTo(MessageStatus.REPORTED);
    assertThat(later.getVendorStatus()).isEqualTo("Queued");
  }

  @Test
  void eachPortServesOnlyItsOwnPaths() throws Exception {
    var body = "{\"messages\":[]}";
    assertThat(callback("/delivery-reports/xabar/wrong-secret/status", body).statusCode())
        .isEqualTo(404);
    assertThat(callback("/v1/messages/hid00000000000000000", "").statusCode()).isEqualTo(404);
    assertThat(callback("/v1/messages", body).statusCode()).isEqualTo(404);
    // On the API port, the callback path does not exist, even with a gateway's certificate.
    assertThatThrownBy(
            () ->
                Callers.gateway(port, "sms.use")
                    .post()
                    .uri("/delivery-reports/xabar/" + SECRET + "/status")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity())
        .isInstanceOfSatisfying(
            RestClientResponseException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
  }

  @Test
  void aCallerWithoutTheRoleIsRefused() {
    var other =
        HttpServiceProxyFactory.builderFor(
                RestClientAdapter.create(Callers.gateway(port, "myid.use")))
            .build()
            .createClient(SmsServiceClient.class);
    assertProblem(() -> other.sendMessage(otp("k-8")), 403, "FORBIDDEN");
  }

  @Test
  void anUnknownMessageIsNotFound() {
    assertProblem(() -> adapter.getMessage("hid00000000000000000"), 404, "SMS_ADAPTER_NOT_FOUND");
  }

  static HttpResponse<String> callback(String path, String body)
      throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + CALLBACK_PORT + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  static int freePort() {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
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
