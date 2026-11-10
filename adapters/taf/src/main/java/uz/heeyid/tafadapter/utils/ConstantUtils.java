package uz.heeyid.tafadapter.utils;

import java.time.Duration;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ConstantUtils {

  /** The one Valkey key of the adapter's TAF token. */
  public static final String TOKEN_KEY = "taf-adapter:token";

  /** A TAF token is renewed this long before TAF's expiry. */
  public static final Duration TOKEN_RENEW_BEFORE = Duration.ofMinutes(5);

  /** SAD TB-5: a token is in Valkey only encrypted with AES-256-GCM. */
  public static final String TOKEN_CIPHER = "AES/GCM/NoPadding";

  public static final int TOKEN_IV_BYTES = 12;
  public static final int TOKEN_TAG_BITS = 128;

  /** TAF's OAuth grant type. */
  public static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";

  /** TAF's SMS templates (templateId). */
  public static final int TEMPLATE_REGISTRATION = 1;

  public static final int TEMPLATE_LOGIN = 2;

  /** TAF's error codes (the `code` of its error body), integration document section 7.6. */
  public static final int TAF_CODE_ALREADY_SENT = 110;

  public static final int TAF_CODE_USER_BLOCKED = 25;
  public static final int TAF_CODE_MY_GOV_FORBIDDEN = 39;
  public static final Set<Integer> TAF_CODES_UNSUPPORTED_DEVICE = Set.of(18, 115);
  public static final Set<Integer> TAF_CODES_NOT_FOUND = Set.of(12, 119);

  /** The agent's health state that means "up". */
  public static final String HEALTH_UP = "UP";
}
