package uz.heeyid.myidadapter.client;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** MyID's media host: the Inplace photo, with no token (the session id is enough). */
@HttpExchange(accept = "application/json")
public interface MyidMediaHttpClient {

  @PostExchange(url = "/api/v1/inplace/upload", contentType = "multipart/form-data")
  ResponseEntity<byte[]> upload(
      @RequestPart("session_id") String sessionId,
      @RequestPart("photo_front") Resource photoFront,
      @RequestPart(name = "photo_focus", required = false) @Nullable Resource photoFocus);
}
