package uz.heeyid.myidadapter.client;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.myidadapter.model.request.MyidCloseWebSessionRequest;
import uz.heeyid.myidadapter.model.request.MyidWebSessionRequest;

/** MyID's WebSDK (#/ru/websdk). */
@HttpExchange(accept = "application/json")
public interface MyidWebHttpClient {

  @PostExchange(url = "/api/v1/web/sessions", contentType = "application/json")
  ResponseEntity<byte[]> openSession(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody MyidWebSessionRequest request);

  @PostExchange(
      url = "/api/v1/web/sessions/{sessionId}/client/close",
      contentType = "application/json")
  ResponseEntity<byte[]> close(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable UUID sessionId,
      @RequestBody MyidCloseWebSessionRequest request);

  @PostExchange("/api/v1/web/sessions/{sessionId}/result")
  ResponseEntity<byte[]> result(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable UUID sessionId);
}
