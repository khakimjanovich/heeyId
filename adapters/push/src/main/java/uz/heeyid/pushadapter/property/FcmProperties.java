package uz.heeyid.pushadapter.property;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The bank's Firebase project and how the adapter calls FCM. No default: a missing value stops
 * startup.
 *
 * @param projectId the bank's Firebase project of this zone
 * @param credentialsFile the service-account JSON of that project, as the Vault agent writes it
 * @param connectTimeout the connection to FCM and to Google's token service
 * @param readTimeout one answer from FCM
 * @param sendTimeout the whole send, the SDK's own retries of a 5xx included (SAD C-15: 5 s)
 * @param breaker the FCM breaker
 */
@Validated
@ConfigurationProperties(prefix = "fcm")
public record FcmProperties(
    @NotBlank String projectId,
    @NotNull Path credentialsFile,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @NotNull Duration sendTimeout,
    @NotNull @Valid Breaker breaker) {

  /**
   * The FCM breaker (SAD §8: 5 failures, probe each 30 s). While it is open, a send gets
   * PUSH_ADAPTER_SERVICE_UNAVAILABLE without a call.
   *
   * @param failures failures in a row that open it
   * @param probeAfter how long it stays open before one probe call
   */
  public record Breaker(@Positive int failures, @NotNull Duration probeAfter) {}
}
