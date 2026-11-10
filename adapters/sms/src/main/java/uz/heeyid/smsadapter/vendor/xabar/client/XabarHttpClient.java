package uz.heeyid.smsadapter.vendor.xabar.client;

import static uz.heeyid.smsadapter.utils.XabarConstants.CONTENT_TYPE;
import static uz.heeyid.smsadapter.utils.XabarConstants.SEND_PATH;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uz.heeyid.smsadapter.vendor.xabar.model.XabarSendRequest;

/** Xabar's broker API; the Basic login is set on the client (XabarConfig). */
@HttpExchange
public interface XabarHttpClient {

  @PostExchange(url = SEND_PATH, contentType = CONTENT_TYPE)
  ResponseEntity<byte[]> send(@RequestBody XabarSendRequest body);
}
