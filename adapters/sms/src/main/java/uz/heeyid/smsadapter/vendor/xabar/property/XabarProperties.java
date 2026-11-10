package uz.heeyid.smsadapter.vendor.xabar.property;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SMS Xabar (Play Mobile SMS-Broker API): where it is, the adapter's login, and the secret of its
 * delivery reports. Bound only when xabar is configured; no default: a missing value stops startup.
 *
 * @param baseUrl the broker API, e.g. https://send.smsxabar.uz/broker-api (POST /send)
 * @param username the adapter's Basic login at Xabar
 * @param password its password
 * @param originator the sender name registered at Xabar
 * @param connectTimeout the wait for a connection; nothing is sent without one
 * @param readTimeout the wait for Xabar's answer; after it the SMS is UNKNOWN
 * @param callbackSecret the secret in the partner URL given to Xabar
 *     (https://<ingress>/delivery-reports/xabar/<secret>); a report with another one is refused
 */
@Validated
@ConfigurationProperties(prefix = "xabar")
public record XabarProperties(
    @NotBlank String baseUrl,
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank String originator,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @NotBlank
        @Pattern(
            regexp = "^[A-Za-z0-9_-]{32,128}$",
            message = "must be 32 to 128 URL-safe characters")
        String callbackSecret) {}
