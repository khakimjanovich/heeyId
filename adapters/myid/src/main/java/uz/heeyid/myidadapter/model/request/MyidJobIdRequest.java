package uz.heeyid.myidadapter.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * MyID A job by its id: POST /api/v1/profile/address and
 * /api/v1/authentication/compare-photos-status.
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyidJobIdRequest {
  @JsonProperty("job_id")
  private String jobId;
}
