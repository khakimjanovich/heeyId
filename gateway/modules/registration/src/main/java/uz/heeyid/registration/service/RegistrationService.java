package uz.heeyid.registration.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.heeyid.notification.service.NotificationService;

/** A person's registration. */
@Service
@RequiredArgsConstructor
public class RegistrationService {

  private final MessageDispatchPort messages;
  private final NotificationService notifications;

  public void register(String phone, String codeText, String idempotencyKey, Duration codeTtl) {
    messages.sendCode(phone, codeText, idempotencyKey, codeTtl);
    notifications.registered(phone);
  }
}
