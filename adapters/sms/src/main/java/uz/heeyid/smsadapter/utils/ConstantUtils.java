package uz.heeyid.smsadapter.utils;

import java.time.Duration;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ConstantUtils {

  /** The length of a message id: Xabar's message-id holds 20 characters at most. */
  public static final int MESSAGE_ID_LENGTH = 20;

  /** The characters of a message id after its prefix (contract: MessageId). */
  public static final String MESSAGE_ID_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

  /** Valkey: a caller's idempotency key, and a message's status (ACL: sms-adapter:*). */
  public static final String VALKEY_KEY_PREFIX = "sms-adapter:key:";

  public static final String VALKEY_MESSAGE_PREFIX = "sms-adapter:message:";

  /** A key is held this much longer than the longest send through every vendor. */
  public static final Duration CLAIM_MARGIN = Duration.ofSeconds(30);

  /** The vendors' delivery reports: only on the callback port, without a token. */
  public static final String DELIVERY_REPORTS_PREFIX = "/delivery-reports/";

  /** The gauge of a vendor's breaker: 1 open, 0 closed; tag vendor. */
  public static final String BREAKER_GAUGE = "sms.vendor.breaker.open";

  public static final String BREAKER_TAG = "vendor";
}
