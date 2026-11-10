package uz.heeyid.myidadapter.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/** MyID POST /api/v1/inplace/sessions. */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyidInplaceSessionRequest {
  @JsonProperty("pass_data")
  private @Nullable String passData;

  private @Nullable String pinfl;

  @JsonProperty("birth_date")
  private @Nullable String birthDate;

  @JsonProperty("is_resident")
  private @Nullable Boolean isResident;

  private @Nullable String reuid;
}
