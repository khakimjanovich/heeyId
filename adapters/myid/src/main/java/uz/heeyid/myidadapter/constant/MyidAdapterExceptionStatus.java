package uz.heeyid.myidadapter.constant;

import uz.khakimjanovich.platform.error.StatusThrowable;

/**
 * The adapter's errors, as the contract names them; MyID's HTTP status and detail travel in
 * subErrors. The HTTP status and the messages are in properties/myid-adapter-exception.yaml.
 */
public enum MyidAdapterExceptionStatus implements StatusThrowable {
  MYID_ADAPTER_CODE_NOT_VALID,
  MYID_ADAPTER_NOT_FOUND,
  MYID_ADAPTER_USER_BLOCKED,
  MYID_ADAPTER_MYID_CLIENT_ERROR,
  MYID_ADAPTER_UNAUTHORIZED,
  MYID_ADAPTER_SERVICE_UNAVAILABLE;

  @Override
  public String getType() {
    return name();
  }
}
