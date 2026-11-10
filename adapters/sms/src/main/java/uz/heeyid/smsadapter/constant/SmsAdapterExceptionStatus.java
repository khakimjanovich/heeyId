package uz.heeyid.smsadapter.constant;

import uz.khakimjanovich.platform.error.StatusThrowable;

/**
 * The adapter's errors, as the contract names them; a vendor's error code and description travel in
 * subErrors. The HTTP status and the messages are in properties/sms-adapter-exception.yaml.
 */
public enum SmsAdapterExceptionStatus implements StatusThrowable {
  SMS_ADAPTER_KEY_CONFLICT,
  SMS_ADAPTER_KEY_IN_USE,
  SMS_ADAPTER_MESSAGE_REFUSED,
  SMS_ADAPTER_SERVICE_UNAVAILABLE,
  SMS_ADAPTER_NOT_FOUND;

  @Override
  public String getType() {
    return name();
  }
}
