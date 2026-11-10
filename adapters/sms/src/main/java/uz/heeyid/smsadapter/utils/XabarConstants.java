package uz.heeyid.smsadapter.utils;

import java.util.Set;
import lombok.experimental.UtilityClass;

/** SMS Xabar (Play Mobile SMS-Broker API, 11.04.2025). */
@UtilityClass
public class XabarConstants {

  /** The vendor's name in sms.active-vendor and sms.failover. */
  public static final String NAME = "xabar";

  /** POST <base-url>/send (section 2.1). */
  public static final String SEND_PATH = "/send";

  /** The doc: Content-Type application/json with charset UTF-8 (section 1). */
  public static final String CONTENT_TYPE = "application/json;charset=UTF-8";

  /**
   * Xabar pushes statuses to <partner-url>/status (section 2.2); the partner URL given to Xabar is
   * https://<ingress>/delivery-reports/xabar/<secret>.
   */
  public static final String STATUS_PATH = "/delivery-reports/xabar/{secret}/status";

  /** Xabar's priority (section 2.1.2.6). */
  public static final String PRIORITY_OTP = "realtime";

  public static final String PRIORITY_NOTICE = "high";

  /**
   * Error codes (HTTP 400) that are Xabar's own fault, not the message's: 100, 102 (section 1.3).
   */
  public static final Set<String> VENDOR_FAULTS = Set.of("100", "102");

  // ponytail: the doc lists no status values; these are the usual SMSC words. Any other value is
  // REPORTED with the raw value kept. Make it exact when Xabar gives the list.
  public static final Set<String> DELIVERED = Set.of("delivered");

  public static final Set<String> EXPIRED = Set.of("expired");

  public static final Set<String> FAILED =
      Set.of(
          "undelivered", "undeliverable", "not-delivered", "not_delivered", "rejected", "failed");
}
