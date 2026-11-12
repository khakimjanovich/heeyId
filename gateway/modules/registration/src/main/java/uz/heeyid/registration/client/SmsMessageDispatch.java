package uz.heeyid.registration.client;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.heeyid.registration.service.MessageDispatchPort;
import uz.khakimjanovich.contracts.smsadapter.api.client.SmsServiceClient;
import uz.khakimjanovich.contracts.smsadapter.api.model.MessageType;
import uz.khakimjanovich.contracts.smsadapter.api.model.SendMessageRequest;

/** {@link MessageDispatchPort} through the SMS adapter (sms-adapter-client). */
@Component
@RequiredArgsConstructor
public class SmsMessageDispatch implements MessageDispatchPort {

  private final SmsServiceClient sms;

  @Override
  public void sendCode(String phone, String text, String idempotencyKey, Duration ttl) {
    sms.sendMessage(
        new SendMessageRequest(
            idempotencyKey, phone, text, MessageType.OTP, Math.toIntExact(ttl.toSeconds())));
  }
}
