package uz.heeyid.smsadapter.vendor;

import org.jspecify.annotations.Nullable;

/**
 * What a vendor said to one send, with the vendor's own code and detail when it gave them.
 *
 * @param kind the outcome
 * @param code the vendor's error code, an HTTP status, or the name of the failure
 * @param detail the vendor's description, or the failure's message
 */
public record VendorOutcome(Kind kind, @Nullable String code, @Nullable String detail) {

  /** The outcomes of a send. */
  public enum Kind {
    /** The vendor took the message. */
    ACCEPTED,
    /** The request went out and no answer came in time: the vendor may send the SMS. */
    UNKNOWN,
    /** The vendor refused the message itself; another vendor would refuse it too. */
    REFUSED,
    /** The vendor cannot take messages now; nothing was sent. The next vendor is tried. */
    UNAVAILABLE
  }

  public static VendorOutcome accepted() {
    return new VendorOutcome(Kind.ACCEPTED, null, null);
  }

  public static VendorOutcome unknown(String code, @Nullable String detail) {
    return new VendorOutcome(Kind.UNKNOWN, code, detail);
  }

  public static VendorOutcome refused(String code, @Nullable String detail) {
    return new VendorOutcome(Kind.REFUSED, code, detail);
  }

  public static VendorOutcome unavailable(String code, @Nullable String detail) {
    return new VendorOutcome(Kind.UNAVAILABLE, code, detail);
  }
}
