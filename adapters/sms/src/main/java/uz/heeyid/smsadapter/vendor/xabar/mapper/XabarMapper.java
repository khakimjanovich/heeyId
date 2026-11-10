package uz.heeyid.smsadapter.vendor.xabar.mapper;

import static uz.heeyid.smsadapter.utils.XabarConstants.DELIVERED;
import static uz.heeyid.smsadapter.utils.XabarConstants.EXPIRED;
import static uz.heeyid.smsadapter.utils.XabarConstants.FAILED;
import static uz.heeyid.smsadapter.utils.XabarConstants.PRIORITY_NOTICE;
import static uz.heeyid.smsadapter.utils.XabarConstants.PRIORITY_OTP;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uz.heeyid.smsadapter.vendor.OutgoingSms;
import uz.heeyid.smsadapter.vendor.xabar.model.XabarErrorResponse;
import uz.heeyid.smsadapter.vendor.xabar.model.XabarSendRequest;
import uz.khakimjanovich.contracts.smsadapter.api.model.MessageStatus;
import uz.khakimjanovich.contracts.smsadapter.api.model.MessageType;

/** Between the adapter and Xabar's bodies. */
@Component
public class XabarMapper {

  private final ObjectMapper json;

  public XabarMapper(ObjectMapper json) {
    this.json = json;
  }

  /** One message; an OTP goes as realtime, a security notice as high. */
  public XabarSendRequest request(OutgoingSms sms, String originator) {
    var priority = sms.type() == MessageType.OTP ? PRIORITY_OTP : PRIORITY_NOTICE;
    return new XabarSendRequest(
        List.of(
            new XabarSendRequest.Item(
                sms.phone(),
                sms.messageId(),
                priority,
                new XabarSendRequest.Sms(
                    originator, sms.ttlSeconds(), new XabarSendRequest.Content(sms.text())))));
  }

  /** The {error_code, error_description} of a 400, or null when the body is not that. */
  public @Nullable XabarErrorResponse error(byte @Nullable [] body) {
    if (body == null || body.length == 0) {
      return null;
    }
    try {
      return json.readValue(body, XabarErrorResponse.class);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** A body as text, for a problem's detail. */
  public @Nullable String text(byte @Nullable [] body) {
    return body == null || body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
  }

  /** Xabar's status word as the contract's status. */
  public MessageStatus status(String vendorStatus) {
    var word = vendorStatus.trim().toLowerCase(Locale.ROOT);
    if (DELIVERED.contains(word)) {
      return MessageStatus.DELIVERED;
    }
    if (EXPIRED.contains(word)) {
      return MessageStatus.EXPIRED;
    }
    return FAILED.contains(word) ? MessageStatus.FAILED : MessageStatus.REPORTED;
  }

  /** Xabar's status-date: ISO 8601 in UTC (section 1), with or without an offset; null if not. */
  public @Nullable Instant at(@Nullable String statusDate) {
    if (statusDate == null || statusDate.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(statusDate).toInstant();
    } catch (DateTimeParseException e) {
      try {
        return LocalDateTime.parse(statusDate.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
      } catch (DateTimeParseException again) {
        return null;
      }
    }
  }

  /** Xabar's message-parts as a number; null if it is not one. */
  public @Nullable Integer parts(@Nullable String messageParts) {
    if (messageParts == null || messageParts.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(messageParts.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
