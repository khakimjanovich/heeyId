package uz.heeyid.myidadapter.property;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where MyID is, how long we wait, and the adapter's MyID credential (one per installation, SAD
 * v3.2 C-1). No default: a missing value stops startup.
 *
 * @param baseUrl MyID's host ({myid_host})
 * @param mediaUrl MyID's media host, for the Inplace photo ({myid_media_host})
 * @param readTimeout the wait for MyID's calls that it answers in 5 s
 * @param longReadTimeout the wait for the calls that MyID answers in 30 s: /users/me, the Inplace
 *     job result and the two compare results
 * @param tokenKey the AES-256 key (base64) that encrypts the MyID tokens in Valkey (SAD TB-5)
 */
@Validated
@ConfigurationProperties(prefix = "myid")
public record MyidProperties(
    @NotBlank String baseUrl,
    @NotBlank String mediaUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @NotNull Duration longReadTimeout,
    @NotNull @Valid Breaker breaker,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9+/]{43}=$", message = "must be 32 bytes in base64")
        String tokenKey,
    @NotNull @Valid Credential credential) {

  /**
   * The MyID breaker (SAD §8: 5 failures, probe each 30 s).
   *
   * @param failures failures in a row that open it
   * @param probeAfter how long it stays open before one probe call
   */
  public record Breaker(@Positive int failures, @NotNull Duration probeAfter) {}

  /**
   * The adapter's MyID credential: the client (Mobile SDK, Inplace, WebSDK, Redirect, QR) and the
   * login of the Backend API without personal data (the password grant). Every part is required.
   */
  public record Credential(
      @NotBlank String clientId,
      @NotBlank String clientSecret,
      @NotBlank String username,
      @NotBlank String password) {}
}
