package uz.heeyid.myidadapter.client;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.myidadapter.model.request.MyidCompareByExternalIdRequest;
import uz.heeyid.myidadapter.model.request.MyidJobIdRequest;

/**
 * The calls MyID answers in up to 30 s (docs.myid.uz): the person's data, the Inplace job result
 * and the compare results. Built on the RestClient that waits {@code myid.long-read-timeout}.
 */
@HttpExchange(accept = "application/json")
public interface MyidLongHttpClient {

  @GetExchange("/api/v1/users/me")
  ResponseEntity<byte[]> me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

  @GetExchange("/api/v1/inplace/jobs/{jobId}")
  ResponseEntity<byte[]> inplaceJob(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable UUID jobId);

  @PostExchange(
      url = "/api/v1/authentication/compare-photos-status",
      contentType = "application/json")
  ResponseEntity<byte[]> compareResult(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody MyidJobIdRequest request);

  @PostExchange(
      url = "/api/v1/authentication/compare-photos-status-by-external",
      contentType = "application/json")
  ResponseEntity<byte[]> compareResultByExternalId(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody MyidCompareByExternalIdRequest request);
}
