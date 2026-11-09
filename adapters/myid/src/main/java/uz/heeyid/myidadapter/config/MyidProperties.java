package uz.heeyid.myidadapter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where MyID is, how long we wait, and each tenant's MyID credential (by X-Tenant-Id). No default:
 * a missing value stops startup.
 *
 * @param baseUrl MyID's host ({myid_host})
 * @param mediaUrl MyID's media host, for the Inplace photo ({myid_media_host})
 * @param tokenKey the AES-256 key (base64) that encrypts the MyID tokens in Valkey (SAD TB-5)
 */
@Validated
@ConfigurationProperties(prefix = "myid")
public record MyidProperties(
    @NotBlank String baseUrl,
    @NotBlank String mediaUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @NotNull @Valid Breaker breaker,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9+/]{43}=$", message = "must be 32 bytes in base64")
        String tokenKey,
    @NotEmpty Map<String, @Valid Tenant> tenants) {

  /**
   * The MyID breaker (SAD §8: 5 failures, probe each 30 s).
   *
   * @param failures failures in a row that open it
   * @param probeAfter how long it stays open before one probe call
   */
  public record Breaker(@Positive int failures, @NotNull Duration probeAfter) {}

  /**
   * A tenant's MyID credential. The login is for the Backend API without personal data (the
   * password grant); a tenant without that contract leaves it out.
   */
  public record Tenant(
      @NotBlank String clientId,
      @NotBlank String clientSecret,
      @Nullable String username,
      @Nullable String password) {}
}
