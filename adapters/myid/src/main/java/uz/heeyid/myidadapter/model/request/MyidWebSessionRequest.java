package uz.heeyid.myidadapter.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** MyID POST /api/v1/web/sessions. */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyidWebSessionRequest {
  @JsonProperty("max_retries")
  private Integer maxRetries;

  @JsonProperty("external_id")
  private String externalId;

  @JsonProperty("ip_address")
  private String ipAddress;
}
