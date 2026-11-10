package uz.heeyid.myidadapter.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.myidadapter.model.request.MyidCompareTaskRequest;

/** MyID's Backend API without personal data (#/ru/compare): starting a task. */
@HttpExchange(accept = "application/json")
public interface MyidCompareHttpClient {

  @PostExchange(
      url = "/api/v1/authentication/compare-photos-task",
      contentType = "application/json")
  ResponseEntity<byte[]> createTask(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody MyidCompareTaskRequest request);
}
