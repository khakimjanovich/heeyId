package uz.heeyid.pushadapter.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ConstantUtils {

  /** The adapter's own FirebaseApp, apart from any default one. */
  public static final String FIREBASE_APP_NAME = "push-adapter";

  /** The two data keys of every message (contract: no other data). */
  public static final String DATA_TYPE = "type";

  public static final String DATA_REFERENCE = "reference";

  /** APNs headers: send now, drop after the lifetime, one notification per reference. */
  public static final String APNS_PRIORITY = "apns-priority";

  public static final String APNS_PRIORITY_NOW = "10";

  public static final String APNS_EXPIRATION = "apns-expiration";

  public static final String APNS_COLLAPSE_ID = "apns-collapse-id";

  /** FCM's header on a QUOTA_EXCEEDED answer. */
  public static final String RETRY_AFTER = "retry-after";

  /** The subError message of an FCM error: FCM's own message is not passed on. */
  public static final String FCM = "FCM";

  /** The gauge of the FCM breaker: 1 open, 0 closed. */
  public static final String BREAKER_GAUGE = "push.fcm.breaker.open";
}
