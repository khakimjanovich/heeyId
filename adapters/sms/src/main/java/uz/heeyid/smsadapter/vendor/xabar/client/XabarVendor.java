package uz.heeyid.smsadapter.vendor.xabar.client;

import static uz.heeyid.smsadapter.utils.XabarConstants.NAME;
import static uz.heeyid.smsadapter.utils.XabarConstants.VENDOR_FAULTS;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import org.springframework.web.client.ResourceAccessException;
import uz.heeyid.smsadapter.vendor.OutgoingSms;
import uz.heeyid.smsadapter.vendor.SmsVendor;
import uz.heeyid.smsadapter.vendor.VendorOutcome;
import uz.heeyid.smsadapter.vendor.xabar.mapper.XabarMapper;
import uz.heeyid.smsadapter.vendor.xabar.property.XabarProperties;

/**
 * SMS Xabar as an {@link SmsVendor}. 200 is ACCEPTED ("Request is received"). A 400 with error 100
 * (Xabar's internal error) or 102 (the account is locked) is Xabar's fault: UNAVAILABLE; any other
 * 400 refuses the message: REFUSED. Any other status (401 wrong login, 404 wrong URL, 429, 5xx) and
 * no connection are UNAVAILABLE. A request that went out and got no answer in time is UNKNOWN.
 */
public class XabarVendor implements SmsVendor {

  private final XabarHttpClient http;
  private final XabarMapper mapper;
  private final XabarProperties properties;

  public XabarVendor(XabarHttpClient http, XabarMapper mapper, XabarProperties properties) {
    this.http = http;
    this.mapper = mapper;
    this.properties = properties;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Duration longestCall() {
    return properties.connectTimeout().plus(properties.readTimeout());
  }

  @Override
  public VendorOutcome send(OutgoingSms sms) {
    try {
      var answer = http.send(mapper.request(sms, properties.originator()));
      var status = answer.getStatusCode().value();
      if (answer.getStatusCode().is2xxSuccessful()) {
        return VendorOutcome.accepted();
      }
      var body = answer.getBody();
      var error = status == 400 ? mapper.error(body) : null;
      if (error == null || error.errorCode() == null) {
        return VendorOutcome.unavailable(String.valueOf(status), mapper.text(body));
      }
      return VENDOR_FAULTS.contains(error.errorCode())
          ? VendorOutcome.unavailable(error.errorCode(), error.errorDescription())
          : VendorOutcome.refused(error.errorCode(), error.errorDescription());
    } catch (ResourceAccessException e) {
      var cause = e.getCause() == null ? e : e.getCause();
      var name = cause.getClass().getSimpleName();
      return notConnected(cause)
          ? VendorOutcome.unavailable(name, cause.getMessage())
          : VendorOutcome.unknown(name, cause.getMessage());
    }
  }

  /** No connection was made, so nothing reached Xabar. */
  private static boolean notConnected(Throwable cause) {
    return cause instanceof HttpConnectTimeoutException
        || cause instanceof ConnectException
        || cause instanceof UnknownHostException
        || cause instanceof UnresolvedAddressException;
  }
}
