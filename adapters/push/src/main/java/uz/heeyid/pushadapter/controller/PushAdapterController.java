package uz.heeyid.pushadapter.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.heeyid.pushadapter.service.PushService;
import uz.khakimjanovich.contracts.pushadapter.api.PushServiceApi;
import uz.khakimjanovich.contracts.pushadapter.api.model.Message;
import uz.khakimjanovich.contracts.pushadapter.api.model.SendMessageRequest;

/** Every operation of push-adapter-api. */
@RestController
@RequiredArgsConstructor
public class PushAdapterController implements PushServiceApi {

  private final PushService push;

  @Override
  public ResponseEntity<Message> sendMessage(SendMessageRequest request) {
    return ResponseEntity.ok(push.send(request));
  }
}
