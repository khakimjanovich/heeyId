package uz.heeyid.smsadapter.vendor.xabar.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** POST <base-url>/send (section 2.1): one message, its own sms channel. */
public record XabarSendRequest(List<Item> messages) {

  /** One element of messages (section 2.1.2.6). */
  public record Item(
      String recipient, @JsonProperty("message-id") String messageId, String priority, Sms sms) {}

  /** The sms channel (section 2.1.2.3): ttl in seconds, then the status is expired. */
  public record Sms(String originator, int ttl, Content content) {}

  /** The sms content (section 2.1.2.4). */
  public record Content(String text) {}
}
