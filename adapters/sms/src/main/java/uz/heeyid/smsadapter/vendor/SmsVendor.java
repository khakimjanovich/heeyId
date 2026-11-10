package uz.heeyid.smsadapter.vendor;

import java.time.Duration;

/**
 * One SMS vendor: the port every vendor module implements (vendor.xabar first). A vendor never
 * throws for its answer: every outcome is a {@link VendorOutcome}. Its delivery reports come in on
 * its own controller, which calls {@code SmsService.report}.
 */
public interface SmsVendor {

  /** The vendor's name in sms.active-vendor and sms.failover. */
  String name();

  /** The longest one send can take: its connect timeout and its read timeout. */
  Duration longestCall();

  /** Sends one SMS. */
  VendorOutcome send(OutgoingSms sms);
}
