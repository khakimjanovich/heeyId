package uz.heeyid.smsadapter.property;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Which vendors send, and how the adapter keeps messages. No default: a missing value stops
 * startup.
 *
 * @param activeVendor the vendor that sends first (xabar)
 * @param failover the vendors tried next, in order, when the one before is down; may be empty
 * @param messageIdPrefix the start of every message id, e.g. hid (the vendor sees it)
 * @param retention how long a message's status and its idempotency key are kept
 * @param breaker each vendor's breaker
 * @param callbackPort the port of the vendors' delivery reports: plain HTTP behind the ingress, no
 *     client certificate; the API port refuses those paths and this port refuses all others
 */
@Validated
@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
    @NotBlank String activeVendor,
    @NotNull List<String> failover,
    @NotBlank @Pattern(regexp = "^[a-z0-9]{1,8}$") String messageIdPrefix,
    @NotNull Duration retention,
    @NotNull @Valid Breaker breaker,
    @Positive int callbackPort) {

  /**
   * A vendor's breaker (SAD §8: 5 failures, probe each 30 s). While it is open, the vendor is
   * skipped and the next one is tried.
   *
   * @param failures failures in a row that open it
   * @param probeAfter how long it stays open before one probe call
   */
  public record Breaker(@Positive int failures, @NotNull Duration probeAfter) {}
}
