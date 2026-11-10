package uz.heeyid.myidadapter.client;

import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_MYID_CLIENT_ERROR;
import static uz.heeyid.myidadapter.utils.ConstantUtils.PNG_SIGNATURE_HEX;
import static uz.heeyid.myidadapter.utils.ConstantUtils.TOKEN_CLAIM_JOB_ID;
import static uz.heeyid.myidadapter.utils.MyidReasonCodeConstants.MYID_POOR_IMAGE_CODES;
import static uz.heeyid.myidadapter.utils.MyidReasonCodeConstants.MYID_SERVICE_UNAVAILABLE_CODES;

import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import uz.khakimjanovich.contracts.myidadapter.api.model.ImageIssue;
import uz.khakimjanovich.contracts.myidadapter.api.model.ReasonKey;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerificationStatus;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * MyID's JSON as the contract's models. The contract keeps MyID's field names in camelCase, so a
 * MyID object is read after its names are turned from snake_case; fields the contract does not have
 * are ignored.
 */
@Component
public class MyidJson {

  private final ObjectMapper mapper;

  public MyidJson(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** A MyID object as a contract model. */
  public <T> T read(JsonNode node, Class<T> type) {
    return mapper.treeToValue(camel(node), type);
  }

  /**
   * MyID's 2xx answer read by {@code reader}. The answer is outside the adapter's control: one that
   * is not of the shape MyID documents is MYID_CLIENT_ERROR with its X-Request-ID, not a 500.
   */
  public static <T> T parse(MyidClient.Answer answer, Function<JsonNode, T> reader) {
    try {
      return Objects.requireNonNull(reader.apply(answer.body()));
    } catch (GeneralException e) {
      throw e;
    } catch (RuntimeException e) {
      throw MyidClient.refusal(MYID_ADAPTER_MYID_CLIENT_ERROR, answer)
          .subError("answer", "not of the documented shape: " + e.getClass().getSimpleName());
    }
  }

  /** A request body from name, value pairs, MyID's names; a null value is left out. */
  public static Map<String, Object> body(@Nullable Object... pairs) {
    var body = new LinkedHashMap<String, Object>();
    for (var i = 0; i < pairs.length; i += 2) {
      if (pairs[i + 1] != null) {
        body.put((String) pairs[i], pairs[i + 1]);
      }
    }
    return body;
  }

  /** The job_id claim of a MyID access token (a JWT), read without a check: it is for support. */
  public @Nullable String jobIdOf(String accessToken) {
    try {
      var start = accessToken.indexOf('.') + 1;
      var payload =
          Base64.getUrlDecoder()
              .decode(accessToken.substring(start, accessToken.indexOf('.', start)));
      JsonNode claims = mapper.readTree(payload);
      var jobId = claims.path(TOKEN_CLAIM_JOB_ID);
      return jobId.isString() ? jobId.asString() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** The contract's group of a MyID outcome code (ReasonKey's description). */
  public static ReasonKey reasonKey(@Nullable Integer code) {
    if (code == null) {
      return ReasonKey.UNKNOWN;
    }
    if (MYID_POOR_IMAGE_CODES.contains(code)) {
      return ReasonKey.POOR_IMAGE;
    }
    if (MYID_SERVICE_UNAVAILABLE_CODES.contains(code)) {
      return ReasonKey.SERVICE_UNAVAILABLE;
    }
    return switch (code) {
      case -1 -> ReasonKey.CONSENT_REFUSED;
      case 1 -> ReasonKey.PASSED;
      case 2 -> ReasonKey.DOCUMENT_DATA_INCORRECT;
      case 3 -> ReasonKey.LIVENESS_NOT_CONFIRMED;
      case 4 -> ReasonKey.FACE_NOT_RECOGNISED;
      case 6 -> ReasonKey.PERSON_DECEASED;
      case 9, 10 -> ReasonKey.TIMED_OUT;
      case 34 -> ReasonKey.DOCUMENT_EXPIRED;
      case 102 -> ReasonKey.CAMERA_ACCESS_REFUSED;
      case 122 -> ReasonKey.BLOCKED_BY_PROVIDER;
      default -> ReasonKey.UNKNOWN;
    };
  }

  /** What is wrong with the image, only for a POOR_IMAGE code. */
  public static @Nullable ImageIssue imageIssue(@Nullable Integer code) {
    if (code == null || !MYID_POOR_IMAGE_CODES.contains(code)) {
      return null;
    }
    return switch (code) {
      case 20 -> ImageIssue.BLURRY;
      case 21 -> ImageIssue.FACE_NOT_FULLY_SHOWN;
      case 22 -> ImageIssue.SEVERAL_FACES;
      case 23 -> ImageIssue.GRAYSCALE;
      case 24 -> ImageIssue.DARK_GLASSES;
      case 26 -> ImageIssue.EYES_CLOSED;
      case 27 -> ImageIssue.HEAD_TURNED;
      case 29 -> ImageIssue.ARTIFACT_OR_REFLECTION;
      case 30 -> ImageIssue.FACE_COVERED;
      default -> ImageIssue.OTHER;
    };
  }

  /** The contract's name of a BNPL validation status. */
  public static VerificationStatus verificationStatus(int status) {
    return switch (status) {
      case 1 -> VerificationStatus.OK;
      case 2 -> VerificationStatus.EXPIRED;
      case 3 -> VerificationStatus.SIGNUP_REQUEST_NOT_FOUND;
      case 4 -> VerificationStatus.CLIENT_NOT_FOUND;
      case 5 -> VerificationStatus.BNPL_NOT_FOUND;
      case 6 -> VerificationStatus.BNPL_BANK_NOT_FOUND;
      default -> VerificationStatus.UNKNOWN;
    };
  }

  /** A data URI of a photo (MyID's compare API takes them so): png or jpeg by its first bytes. */
  public static String dataUri(byte[] photo) {
    return "data:image/"
        + (isPng(photo) ? "png" : "jpeg")
        + ";base64,"
        + Base64.getEncoder().encodeToString(photo);
  }

  /** A PNG by its 8-byte signature; MyID takes png or jpeg, so anything else is sent as jpeg. */
  public static boolean isPng(byte[] photo) {
    var png = HexFormat.of().parseHex(PNG_SIGNATURE_HEX);
    return photo.length >= png.length && Arrays.equals(photo, 0, png.length, png, 0, png.length);
  }

  static JsonNode camel(JsonNode node) {
    if (node instanceof ObjectNode object) {
      var copy = object.objectNode();
      object.properties().forEach(e -> copy.set(camel(e.getKey()), camel(e.getValue())));
      return copy;
    }
    if (node instanceof ArrayNode array) {
      var copy = array.arrayNode();
      array.forEach(item -> copy.add(camel(item)));
      return copy;
    }
    return node;
  }

  static String camel(String name) {
    var out = new StringBuilder(name.length());
    var upper = false;
    for (var i = 0; i < name.length(); i++) {
      var c = name.charAt(i);
      if (c == '_') {
        upper = !out.isEmpty();
      } else {
        out.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return out.toString();
  }
}
