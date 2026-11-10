package uz.heeyid.tafadapter.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** The TAF agent's health (taf.health-url), integration document section 7.4: with the token. */
@HttpExchange(accept = "application/json")
public interface TafHealthHttpClient {

  @GetExchange
  ResponseEntity<byte[]> health(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
