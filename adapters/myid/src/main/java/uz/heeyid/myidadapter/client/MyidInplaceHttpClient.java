package uz.heeyid.myidadapter.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.myidadapter.model.request.MyidInplaceJobRequest;
import uz.heeyid.myidadapter.model.request.MyidInplaceSessionRequest;

/** MyID's Inplace, new flow (#/ru/myid_inplace_new_flow), on the main host. */
@HttpExchange(accept = "application/json")
public interface MyidInplaceHttpClient {

  @PostExchange(url = "/api/v1/inplace/sessions", contentType = "application/json")
  ResponseEntity<byte[]> openSession(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody MyidInplaceSessionRequest request);

  @PostExchange(url = "/api/v1/inplace/jobs", contentType = "application/json")
  ResponseEntity<byte[]> createJob(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody MyidInplaceJobRequest request);
}
