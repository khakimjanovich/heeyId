package uz.heeyid.smsadapter.vendor.xabar.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Xabar's status push to <partner-url>/status (section 2.2): one or many statuses. */
public record XabarStatusRequest(@Nullable List<Report> messages) {

  /** One status, as Xabar sends it; every field may be missing. */
  public record Report(
      @JsonProperty("message-id") @Nullable String messageId,
      @Nullable String channel,
      @Nullable String status,
      @JsonProperty("status-date") @Nullable String statusDate,
      @Nullable String description,
      @Nullable String operator,
      @JsonProperty("message-parts") @Nullable String messageParts) {}
}
