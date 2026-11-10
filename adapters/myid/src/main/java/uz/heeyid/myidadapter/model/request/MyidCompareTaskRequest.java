package uz.heeyid.myidadapter.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/** MyID POST /api/v1/authentication/compare-photos-task; photos as Data URIs. */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyidCompareTaskRequest {
  @JsonProperty("photo_from_camera")
  private PhotoFromCamera photoFromCamera;

  @JsonProperty("photo_from_passport")
  private @Nullable String photoFromPassport;

  @JsonProperty("agreed_on_terms")
  private Boolean agreedOnTerms;

  @JsonProperty("client_id")
  private String clientId;

  private Boolean liveness;
  private Boolean comparison;

  @JsonProperty("external_id")
  private @Nullable String externalId;

  private @Nullable Double threshold;
  private @Nullable String pinfl;

  @JsonProperty("pass_data")
  private @Nullable String passData;

  private @Nullable String phone;

  /** The camera's face photo, as a Data URI. */
  public record PhotoFromCamera(String front) {}
}
