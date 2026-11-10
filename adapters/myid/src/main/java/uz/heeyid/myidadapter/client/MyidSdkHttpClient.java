package uz.heeyid.myidadapter.client;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.myidadapter.model.request.MyidJobIdRequest;
import uz.heeyid.myidadapter.model.request.MyidSdkSessionRequest;

/** MyID's Mobile SDK, new flow (docs.myid.uz #/ru/sdknew). */
@HttpExchange(accept = "application/json")
public interface MyidSdkHttpClient {

  @PostExchange(url = "/api/v2/sdk/sessions", contentType = "application/json")
  ResponseEntity<byte[]> openSession(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody MyidSdkSessionRequest request);

  @GetExchange("/api/v1/sdk/data")
  ResponseEntity<byte[]> data(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam("code") String code);

  @GetExchange("/api/v1/sdk/sessions/{sessionId}")
  ResponseEntity<byte[]> session(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable UUID sessionId);

  @PostExchange(url = "/api/v1/profile/address", contentType = "application/json")
  ResponseEntity<byte[]> address(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody MyidJobIdRequest request);
}
