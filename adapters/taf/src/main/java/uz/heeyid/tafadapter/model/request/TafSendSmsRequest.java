package uz.heeyid.tafadapter.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/** TAF POST /api/accounts/v1/sms. The phone without +; deviceName only for a login. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TafSendSmsRequest(
    String phone, String requestId, String code, int templateId, @Nullable String deviceName) {}
