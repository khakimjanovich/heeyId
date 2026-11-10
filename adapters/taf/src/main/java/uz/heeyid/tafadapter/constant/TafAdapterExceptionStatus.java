package uz.heeyid.tafadapter.constant;

import uz.khakimjanovich.platform.error.StatusThrowable;

/**
 * The adapter's errors, as the contract names them; TAF's HTTP status, code and message travel in
 * subErrors. The HTTP status and the messages are in properties/taf-adapter-exception.yaml.
 */
public enum TafAdapterExceptionStatus implements StatusThrowable {
  TAF_ADAPTER_CODE_ALREADY_SENT,
  TAF_ADAPTER_NUMBER_BLOCKED,
  TAF_ADAPTER_TRUSTED_BANK_REFUSED,
  TAF_ADAPTER_DEVICE_NOT_SUPPORTED,
  TAF_ADAPTER_NOT_FOUND,
  TAF_ADAPTER_TAF_CLIENT_ERROR,
  TAF_ADAPTER_UNAUTHORIZED,
  TAF_ADAPTER_SERVICE_UNAVAILABLE;

  @Override
  public String getType() {
    return name();
  }
}
