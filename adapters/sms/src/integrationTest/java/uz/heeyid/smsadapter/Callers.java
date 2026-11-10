package uz.heeyid.smsadapter;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.web.client.RestClient;

/**
 * The adapter's caller for the integration tests, as in production: a heeyID gateway over mutual
 * TLS with a certificate of heeyID's CA and a token from heeyID's Keycloak.
 */
final class Callers {

  static final String AUDIENCE = "sms-adapter";

  private static final Path DIR = directory();
  static final ThrowawayCa HEEYID_CA = new ThrowawayCa(DIR, "heeyid");
  static final ThrowawayCa.Party SERVER = HEEYID_CA.server("sms-adapter");
  static final ThrowawayCa.Party GATEWAY = HEEYID_CA.client("customer-gateway");
  static final TestIssuer ISSUER = new TestIssuer();

  private Callers() {}

  /** The adapter's settings for the caller: issuer, audience, TLS. */
  static void register(DynamicPropertyRegistry registry) {
    registry.add("SMS_ADAPTER_ISSUER_URI", ISSUER::uri);
    registry.add("SMS_ADAPTER_AUDIENCE", () -> AUDIENCE);
    registry.add("SMS_ADAPTER_SSL_CERTIFICATE", () -> "file:" + SERVER.certificate());
    registry.add("SMS_ADAPTER_SSL_PRIVATE_KEY", () -> "file:" + SERVER.privateKey());
    registry.add("SMS_ADAPTER_SSL_TRUSTED_CAS", () -> "file:" + HEEYID_CA.certificate());
  }

  /** The gateway over mutual TLS, with a token that carries {@code roles}. */
  static RestClient gateway(int port, String... roles) {
    var token = ISSUER.token("customer-gateway", AUDIENCE, roles);
    return RestClient.builder()
        .baseUrl("https://localhost:" + port)
        .requestFactory(
            new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .sslContext(ThrowawayCa.tls(GATEWAY, HEEYID_CA.certificate()))
                    .build()))
        .defaultHeaders(h -> h.setBearerAuth(token))
        .build();
  }

  static Path directory() {
    try {
      return Files.createTempDirectory("adapter-it");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
