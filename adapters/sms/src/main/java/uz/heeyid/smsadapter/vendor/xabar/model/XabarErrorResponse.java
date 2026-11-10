package uz.heeyid.smsadapter.vendor.xabar.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/** The body of Xabar's HTTP 400 (section 1.2). */
public record XabarErrorResponse(
    @JsonProperty("error_code") @Nullable String errorCode,
    @JsonProperty("error_description") @Nullable String errorDescription) {}
