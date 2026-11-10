package uz.heeyid.pushadapter.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static uz.heeyid.pushadapter.constant.PushAdapterExceptionStatus.PUSH_ADAPTER_RATE_LIMITED;
import static uz.heeyid.pushadapter.constant.PushAdapterExceptionStatus.PUSH_ADAPTER_SERVICE_UNAVAILABLE;
import static uz.heeyid.pushadapter.constant.PushAdapterExceptionStatus.PUSH_ADAPTER_TOKEN_NOT_VALID;
import static uz.heeyid.pushadapter.utils.ConstantUtils.FCM;
import static uz.heeyid.pushadapter.utils.ConstantUtils.RETRY_AFTER;

import com.google.firebase.ErrorCode;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import uz.heeyid.pushadapter.mapper.FcmMessages;
import uz.heeyid.pushadapter.property.FcmProperties;
import uz.khakimjanovich.contracts.pushadapter.api.model.Message;
import uz.khakimjanovich.contracts.pushadapter.api.model.SendMessageRequest;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * Sends one push message through FCM. It keeps no state and does not retry: the outcome tells the
 * caller what to do (contract push-adapter-api). Only the reference, the type, the outcome and the
 * time reach a log, never the token.
 */
@Slf4j
@Service
public class PushService {

  private final FirebaseMessaging messaging;
  private final FcmBreaker breaker;
  private final FcmProperties properties;
  private final Clock clock;

  public PushService(
      FirebaseMessaging messaging, FcmBreaker breaker, FcmProperties properties, Clock clock) {
    this.messaging = messaging;
    this.breaker = breaker;
    this.properties = properties;
    this.clock = clock;
  }

  public Message send(SendMessageRequest request) {
    var reference = request.getReference();
    var type = request.getType();
    if (!breaker.allows()) {
      log.warn("push {} {} not sent: breaker open", reference, type);
      throw new GeneralException(PUSH_ADAPTER_SERVICE_UNAVAILABLE).subError(FCM, "breaker open");
    }
    var started = clock.millis();
    // ponytail: the SDK retries a 5xx on its own and has no public switch for it; the send
    // timeout caps the whole send. Turn the SDK's retries off when it offers a switch.
    var future = messaging.sendAsync(FcmMessages.of(request, clock.instant()));
    try {
      var messageId = future.get(properties.sendTimeout().toMillis(), MILLISECONDS);
      breaker.answered();
      log.info("push {} {} sent in {} ms", reference, type, clock.millis() - started);
      return new Message(messageId);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof FirebaseMessagingException refusal) {
        throw refused(reference, type.getValue(), refusal, clock.millis() - started);
      }
      breaker.failed();
      log.warn("push {} {} failed in {} ms: {}", reference, type, clock.millis() - started, e);
      throw new GeneralException(PUSH_ADAPTER_SERVICE_UNAVAILABLE).subError(FCM, "no answer");
    } catch (TimeoutException e) {
      future.cancel(true);
      breaker.failed();
      log.warn("push {} {} no answer in {} ms", reference, type, clock.millis() - started);
      throw new GeneralException(PUSH_ADAPTER_SERVICE_UNAVAILABLE).subError(FCM, "timeout");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      throw new GeneralException(PUSH_ADAPTER_SERVICE_UNAVAILABLE).subError(FCM, "interrupted");
    }
  }

  /** FCM's error as the contract's outcome; FCM's own message is not passed on. */
  private GeneralException refused(
      String reference, String type, FirebaseMessagingException refusal, long millis) {
    var messagingCode = refusal.getMessagingErrorCode();
    var code = messagingCode != null ? messagingCode.name() : refusal.getErrorCode().name();
    log.warn("push {} {} refused by FCM in {} ms: {}", reference, type, millis, code);
    if (isTokenNotValid(messagingCode, refusal.getErrorCode())) {
      breaker.answered();
      return new GeneralException(PUSH_ADAPTER_TOKEN_NOT_VALID).subError(code, FCM);
    }
    if (messagingCode == MessagingErrorCode.QUOTA_EXCEEDED
        || (messagingCode == null && refusal.getErrorCode() == ErrorCode.RESOURCE_EXHAUSTED)) {
      breaker.answered();
      var limited = new GeneralException(PUSH_ADAPTER_RATE_LIMITED).subError(code, FCM);
      var wait = retryAfter(refusal.getHttpResponse());
      return wait == null ? limited : limited.retryAfter(wait);
    }
    breaker.failed();
    return new GeneralException(PUSH_ADAPTER_SERVICE_UNAVAILABLE).subError(code, FCM);
  }

  /**
   * The adapter builds every field but the token from the contract, so INVALID_ARGUMENT is the
   * token too.
   */
  private static boolean isTokenNotValid(
      @Nullable MessagingErrorCode messagingCode, ErrorCode errorCode) {
    return messagingCode == MessagingErrorCode.UNREGISTERED
        || messagingCode == MessagingErrorCode.SENDER_ID_MISMATCH
        || messagingCode == MessagingErrorCode.INVALID_ARGUMENT
        || (messagingCode == null && errorCode == ErrorCode.INVALID_ARGUMENT);
  }

  /** FCM's Retry-After in seconds, when it gave one. */
  private static @Nullable Duration retryAfter(@Nullable IncomingHttpResponse response) {
    if (response == null) {
      return null;
    }
    for (var header : response.getHeaders().entrySet()) {
      if (header.getKey().equalsIgnoreCase(RETRY_AFTER)) {
        var value = header.getValue() instanceof List<?> list ? list.getFirst() : header.getValue();
        try {
          return Duration.ofSeconds(Long.parseLong(String.valueOf(value).trim()));
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }
    return null;
  }
}
