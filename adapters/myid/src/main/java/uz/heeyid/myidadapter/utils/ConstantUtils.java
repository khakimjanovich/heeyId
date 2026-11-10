package uz.heeyid.myidadapter.utils;

import java.time.Duration;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ConstantUtils {

  /** A key of a QR code's own pairs (contract: QrContentRequest.extraInfo). */
  public static final Pattern QR_EXTRA_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,50}$");

  /** A value of a QR code's own pairs: no character can change the QR text. */
  public static final Pattern QR_EXTRA_VALUE = Pattern.compile("^[A-Za-z0-9._~-]{1,100}$");

  /** The claim of MyID's access token that holds the job id (Redirect, QR, WebSDK). */
  public static final String TOKEN_CLAIM_JOB_ID = "job_id";

  /** The 8-byte PNG signature, in hex; MyID takes png or jpeg. */
  public static final String PNG_SIGNATURE_HEX = "89504e470d0a1a0a";

  /** A MyID token is renewed this long before MyID's expiry. */
  public static final Duration TOKEN_RENEW_BEFORE = Duration.ofMinutes(5);

  /** SAD TB-5: a token is in Valkey only encrypted with AES-256-GCM. */
  public static final String TOKEN_CIPHER = "AES/GCM/NoPadding";

  public static final int TOKEN_IV_BYTES = 12;
  public static final int TOKEN_TAG_BITS = 128;
}
