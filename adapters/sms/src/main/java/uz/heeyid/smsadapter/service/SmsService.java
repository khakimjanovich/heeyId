package uz.heeyid.smsadapter.service;

import static uz.heeyid.smsadapter.constant.SmsAdapterExceptionStatus.SMS_ADAPTER_MESSAGE_REFUSED;
import static uz.heeyid.smsadapter.constant.SmsAdapterExceptionStatus.SMS_ADAPTER_NOT_FOUND;
import static uz.heeyid.smsadapter.constant.SmsAdapterExceptionStatus.SMS_ADAPTER_SERVICE_UNAVAILABLE;
import static uz.heeyid.smsadapter.utils.ConstantUtils.CLAIM_MARGIN;
import static uz.heeyid.smsadapter.utils.ConstantUtils.MESSAGE_ID_ALPHABET;
import static uz.heeyid.smsadapter.utils.ConstantUtils.MESSAGE_ID_LENGTH;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import uz.heeyid.smsadapter.property.SmsProperties;
import uz.heeyid.smsadapter.store.MessageStore;
import uz.heeyid.smsadapter.store.StoredMessage;
import uz.heeyid.smsadapter.vendor.OutgoingSms;
import uz.heeyid.smsadapter.vendor.VendorOutcome.Kind;
import uz.heeyid.smsadapter.vendor.Vendors;
import uz.khakimjanovich.contracts.smsadapter.api.model.Message;
import uz.khakimjanovich.contracts.smsadapter.api.model.MessageStatus;
import uz.khakimjanovich.contracts.smsadapter.api.model.SendMessageRequest;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * Sends an SMS through the vendors in order and keeps its status. A vendor that is down (its
 * breaker open, no connection, its own fault) passes the SMS to the next one; a vendor that took
 * the request and gave no answer in time ends the send as UNKNOWN, so no code goes out twice. No
 * text or phone number reaches a log.
 */
@Slf4j
@Service
public class SmsService {

  private final Vendors vendors;
  private final MessageStore store;
  private final SmsProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public SmsService(Vendors vendors, MessageStore store, SmsProperties properties, Clock clock) {
    this.vendors = vendors;
    this.store = store;
    this.properties = properties;
    this.clock = clock;
  }

  public Message send(SendMessageRequest request) {
    var key = request.getIdempotencyKey();
    var hash = hash(request);
    var earlier = store.claim(key, hash, vendors.longestSend().plus(CLAIM_MARGIN));
    if (earlier != null) {
      return message(earlier);
    }
    var sms =
        new OutgoingSms(
            newId(),
            request.getPhone(),
            request.getText(),
            request.getType(),
            request.getTtlSeconds());
    var down = new GeneralException(SMS_ADAPTER_SERVICE_UNAVAILABLE);
    for (var entry : vendors.ordered()) {
      var vendor = entry.vendor().name();
      if (!entry.breaker().allows()) {
        down.subError(vendor, "breaker open");
        continue;
      }
      var outcome = entry.vendor().send(sms);
      if (outcome.kind() == Kind.ACCEPTED || outcome.kind() == Kind.REFUSED) {
        entry.breaker().answered();
      } else {
        entry.breaker().failed();
      }
      switch (outcome.kind()) {
        case ACCEPTED, UNKNOWN -> {
          var status =
              outcome.kind() == Kind.ACCEPTED ? MessageStatus.ACCEPTED : MessageStatus.UNKNOWN;
          log.info(
              "sms {} {} by {}{}",
              sms.messageId(),
              status,
              vendor,
              outcome.code() == null ? "" : " (" + outcome.code() + ")");
          var stored =
              new StoredMessage(
                  sms.messageId(), vendor, sms.type(), status, null, null, clock.instant());
          store.sent(key, hash, stored, properties.retention());
          return message(stored);
        }
        case REFUSED -> {
          log.warn("sms {} refused by {}: error {}", sms.messageId(), vendor, outcome.code());
          store.release(key);
          throw new GeneralException(SMS_ADAPTER_MESSAGE_REFUSED)
              .subError(outcome.code(), outcome.detail());
        }
        case UNAVAILABLE -> {
          log.warn("sms {} not taken by {}: {}", sms.messageId(), vendor, outcome.code());
          down.subError(vendor + " " + outcome.code(), outcome.detail());
        }
      }
    }
    store.release(key);
    throw down;
  }

  public Message get(String messageId) {
    return store
        .find(messageId)
        .map(SmsService::message)
        .orElseThrow(() -> new GeneralException(SMS_ADAPTER_NOT_FOUND));
  }

  /**
   * A vendor's delivery report. A report for a message the adapter does not have (or no longer
   * keeps), or that another vendor sent, changes nothing.
   */
  public void report(
      String vendor,
      String messageId,
      MessageStatus status,
      String vendorStatus,
      @Nullable Integer parts,
      @Nullable Instant at) {
    var known = store.find(messageId).filter(m -> m.vendor().equals(vendor));
    if (known.isEmpty()) {
      log.info("delivery report of {} for an unknown sms {}: ignored", vendor, messageId);
      return;
    }
    var m = known.get();
    store.save(
        new StoredMessage(
            m.messageId(),
            m.vendor(),
            m.type(),
            status,
            vendorStatus,
            parts,
            at == null ? clock.instant() : at),
        properties.retention());
    log.info("sms {} {} ({}) by {}", messageId, status, vendorStatus, vendor);
  }

  private static Message message(StoredMessage m) {
    return new Message(
            m.messageId(), m.vendor(), m.type(), m.status(), m.statusAt().atOffset(ZoneOffset.UTC))
        .vendorStatus(m.vendorStatus())
        .parts(m.parts());
  }

  /** The prefix, then random base36 characters: 20 in all (Xabar's message-id limit). */
  private String newId() {
    var prefix = properties.messageIdPrefix();
    var id = new StringBuilder(MESSAGE_ID_LENGTH).append(prefix);
    while (id.length() < MESSAGE_ID_LENGTH) {
      id.append(MESSAGE_ID_ALPHABET.charAt(random.nextInt(MESSAGE_ID_ALPHABET.length())));
    }
    return id.toString();
  }

  /** The request's fingerprint: the same key with another request is a conflict. */
  private static String hash(SendMessageRequest r) {
    // ponytail: plain SHA-256; Valkey is ACL-closed and an OTP lives minutes. Use an HMAC with a
    // key of its own if Valkey ever leaves the segment.
    var text =
        String.join(
            "\n", r.getPhone(), r.getType().getValue(), r.getTtlSeconds().toString(), r.getText());
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
