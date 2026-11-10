package uz.heeyid.smsadapter.vendor;

import java.time.Duration;
import java.util.List;

/**
 * The configured vendors in the order they are tried: sms.active-vendor, then sms.failover.
 *
 * @param ordered each vendor with its breaker
 */
public record Vendors(List<Entry> ordered) {

  /** A vendor and its breaker. */
  public record Entry(SmsVendor vendor, VendorBreaker breaker) {}

  /** The longest one send can take through every vendor. */
  public Duration longestSend() {
    return ordered.stream()
        .map(e -> e.vendor().longestCall())
        .reduce(Duration.ZERO, Duration::plus);
  }
}
