package uz.heeyid.myidadapter.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.myidadapter.model.request.MyidBnplVerifyRequest;

/**
 * MyID's identification validation (#/ru/bnpl): JSON with no credential, as the contract says until
 * MyID answers.
 */
@HttpExchange(accept = "application/json")
public interface MyidValidationHttpClient {

  @PostExchange(url = "/api/v1/bnpl/verify", contentType = "application/json")
  ResponseEntity<byte[]> verify(@RequestBody MyidBnplVerifyRequest request);
}
