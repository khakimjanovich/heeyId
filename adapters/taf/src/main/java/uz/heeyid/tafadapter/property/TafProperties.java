package uz.heeyid.tafadapter.property;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where TAF is, how long we wait, and the adapter's TAF credential (one per installation). No
 * default: a missing value stops startup.
 *
 * @param authUrl TAF's OAuth 2.0 token endpoint (the full URL)
 * @param agentUrl the TAF agent in the network of the bank (its API host)
 * @param healthUrl the TAF agent's health (agent host, port 8081, /actuator/health; the full URL)
 * @param readTimeout the wait for each TAF call (SAD C-15: 5 s)
 * @param tokenKey the AES-256 key (base64) that encrypts the TAF token in Valkey (SAD TB-5)
 */
@Validated
@ConfigurationProperties(prefix = "taf")
public record TafProperties(
    @NotBlank String authUrl,
    @NotBlank String agentUrl,
    @NotBlank String healthUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @NotNull @Valid Breaker breaker,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9+/]{43}=$", message = "must be 32 bytes in base64")
        String tokenKey,
    @NotNull @Valid Credential credential) {

  /**
   * The TAF breaker (SAD C-15: 5 failures, probe each 30 s).
   *
   * @param failures failures in a row that open it
   * @param probeAfter how long it stays open before one probe call
   */
  public record Breaker(@Positive int failures, @NotNull Duration probeAfter) {}

  /** The adapter's TAF credential (client_credentials). Every part is required. */
  public record Credential(
      @NotBlank String clientId, @NotBlank String clientSecret, @NotBlank String scope) {}
}
