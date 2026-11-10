package uz.heeyid.smsadapter.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.heeyid.smsadapter.service.SmsService;
import uz.khakimjanovich.contracts.smsadapter.api.SmsServiceApi;
import uz.khakimjanovich.contracts.smsadapter.api.model.Message;
import uz.khakimjanovich.contracts.smsadapter.api.model.SendMessageRequest;

/** Every operation of sms-adapter-api. */
@RestController
@RequiredArgsConstructor
public class SmsAdapterController implements SmsServiceApi {

  private final SmsService sms;

  @Override
  public ResponseEntity<Message> sendMessage(SendMessageRequest request) {
    return ResponseEntity.ok(sms.send(request));
  }

  @Override
  public ResponseEntity<Message> getMessage(String messageId) {
    return ResponseEntity.ok(sms.get(messageId));
  }
}
