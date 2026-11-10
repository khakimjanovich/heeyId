package uz.heeyid.myidadapter.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** MyID POST /api/v1/oauth2/refresh-token (JSON, as Redirect and QR inplace say). */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyidRefreshTokenRequest {
  @JsonProperty("refresh_token")
  private String refreshToken;

  @JsonProperty("client_id")
  private String clientId;
}
