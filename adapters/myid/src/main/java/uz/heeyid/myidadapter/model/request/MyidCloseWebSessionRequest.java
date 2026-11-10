package uz.heeyid.myidadapter.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** MyID POST /api/v1/web/sessions/{session_id}/client/close. */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyidCloseWebSessionRequest {
  private Integer code;
}
