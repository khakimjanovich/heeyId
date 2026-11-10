package uz.heeyid.myidadapter.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** MyID POST /api/v1/bnpl/verify (JSON, as the contract says until MyID answers). */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyidBnplVerifyRequest {
  @JsonProperty("job_id")
  private String jobId;

  @JsonProperty("bank_id")
  private String bankId;

  @JsonProperty("bnpl_id")
  private String bnplId;

  private String pinfl;
}
