package uz.heeyid.myidadapter.client;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import uz.khakimjanovich.contracts.myidadapter.api.model.ImageIssue;
import uz.khakimjanovich.contracts.myidadapter.api.model.ReasonKey;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerificationStatus;

/**
 * MyID's JSON as the contract's models. The contract keeps MyID's field names in camelCase, so a
 * MyID object is read after its names are turned from snake_case; fields the contract does not have
 * are ignored.
 */
@Component
public class MyidJson {

  private static final Set<Integer> POOR_IMAGE =
      Set.of(14, 17, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32);
  private static final Set<Integer> SERVICE_UNAVAILABLE =
      Set.of(5, 11, 12, 13, 15, 16, 18, 19, 33, 38, 101, 103);

  private static final String JOB_ID = "job_id";

  private final ObjectMapper mapper;

  public MyidJson(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** A MyID object as a contract model. */
  public <T> T read(JsonNode node, Class<T> type) {
    return mapper.treeToValue(camel(node), type);
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
      var jobId = claims.path(JOB_ID);
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
    if (POOR_IMAGE.contains(code)) {
      return ReasonKey.POOR_IMAGE;
    }
    if (SERVICE_UNAVAILABLE.contains(code)) {
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
    if (code == null || !POOR_IMAGE.contains(code)) {
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
    var png = photo.length > 3 && photo[0] == (byte) 0x89 && photo[1] == 'P';
    return "data:image/"
        + (png ? "png" : "jpeg")
        + ";base64,"
        + Base64.getEncoder().encodeToString(photo);
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
