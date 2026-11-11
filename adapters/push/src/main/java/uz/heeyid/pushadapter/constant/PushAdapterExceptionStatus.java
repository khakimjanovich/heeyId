package uz.heeyid.pushadapter.constant;

import uz.khakimjanovich.platform.error.StatusThrowable;

/**
 * The adapter's errors, as the contract names them; FCM's error code travels in subErrors. The HTTP
 * status and the messages are in properties/push-adapter-exception.yaml.
 */
public enum PushAdapterExceptionStatus implements StatusThrowable {
  PUSH_ADAPTER_TOKEN_NOT_VALID,
  PUSH_ADAPTER_RATE_LIMITED,
  PUSH_ADAPTER_SERVICE_UNAVAILABLE;

  @Override
  public String getType() {
    return name();
  }
}
