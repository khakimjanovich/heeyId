package uz.heeyid.smsadapter.store;

import static uz.heeyid.smsadapter.constant.SmsAdapterExceptionStatus.SMS_ADAPTER_KEY_CONFLICT;
import static uz.heeyid.smsadapter.constant.SmsAdapterExceptionStatus.SMS_ADAPTER_KEY_IN_USE;
import static uz.heeyid.smsadapter.utils.ConstantUtils.VALKEY_KEY_PREFIX;
import static uz.heeyid.smsadapter.utils.ConstantUtils.VALKEY_MESSAGE_PREFIX;

import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * Idempotency keys and message statuses in Valkey, shared by every adapter instance. A key is
 * claimed with SET NX before the send, so two instances never send for the same key; it then points
 * at its message. Valkey keeps no text and no phone number: a key holds only a SHA-256 of the
 * request.
 */
@Component
public class MessageStore {

  /** A claimed key: the request's hash, and its message once sent (null while sending). */
  record KeyRecord(String hash, @Nullable String messageId) {}

  private final StringRedisTemplate valkey;
  private final ObjectMapper mapper;

  public MessageStore(StringRedisTemplate valkey, ObjectMapper mapper) {
    this.valkey = valkey;
    this.mapper = mapper;
  }

  /**
   * Claims {@code key} for a send of the request with {@code hash}, for {@code hold}. Null: the key
   * is this call's now. A message: the key was sent before with the same request. A key used with
   * another request is KEY_CONFLICT; one still sending is KEY_IN_USE.
   */
  public @Nullable StoredMessage claim(String key, String hash, Duration hold) {
    var name = VALKEY_KEY_PREFIX + key;
    var claimed = valkey.opsForValue().setIfAbsent(name, json(new KeyRecord(hash, null)), hold);
    if (Boolean.TRUE.equals(claimed)) {
      return null;
    }
    var raw = valkey.opsForValue().get(name);
    if (raw == null) {
      // It expired between the two calls: a send that never finished. Claim it again.
      return claim(key, hash, hold);
    }
    var existing = mapper.readValue(raw, KeyRecord.class);
    if (!existing.hash().equals(hash)) {
      throw new GeneralException(SMS_ADAPTER_KEY_CONFLICT);
    }
    var messageId = existing.messageId();
    if (messageId == null) {
      throw new GeneralException(SMS_ADAPTER_KEY_IN_USE);
    }
    return find(messageId).orElseThrow(() -> new GeneralException(SMS_ADAPTER_KEY_IN_USE));
  }

  /** Keeps the message and points the key at it, both for {@code retention}. */
  public void sent(String key, String hash, StoredMessage message, Duration retention) {
    save(message, retention);
    valkey
        .opsForValue()
        .set(VALKEY_KEY_PREFIX + key, json(new KeyRecord(hash, message.messageId())), retention);
  }

  /** Frees a key whose send sent nothing, so the caller may try again with it. */
  public void release(String key) {
    valkey.delete(VALKEY_KEY_PREFIX + key);
  }

  public Optional<StoredMessage> find(String messageId) {
    var raw = valkey.opsForValue().get(VALKEY_MESSAGE_PREFIX + messageId);
    return raw == null ? Optional.empty() : Optional.of(mapper.readValue(raw, StoredMessage.class));
  }

  /** Writes the message's status, kept for {@code retention} from now. */
  public void save(StoredMessage message, Duration retention) {
    valkey.opsForValue().set(VALKEY_MESSAGE_PREFIX + message.messageId(), json(message), retention);
  }

  private String json(Object value) {
    return mapper.writeValueAsString(value);
  }
}
