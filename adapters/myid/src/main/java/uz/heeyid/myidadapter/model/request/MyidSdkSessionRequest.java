package uz.heeyid.myidadapter.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/** MyID POST /api/v2/sdk/sessions: a full proofing (a document, or none) or a re-check (reuid). */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyidSdkSessionRequest {
  private Double threshold;
  private @Nullable String pinfl;

  @JsonProperty("pass_data")
  private @Nullable String passData;

  @JsonProperty("birth_date")
  private @Nullable String birthDate;

  @JsonProperty("is_resident")
  private @Nullable Boolean isResident;

  @JsonProperty("phone_number")
  private @Nullable String phoneNumber;

  private @Nullable String reuid;
}
