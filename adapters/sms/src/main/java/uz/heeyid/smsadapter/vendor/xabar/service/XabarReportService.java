package uz.heeyid.smsadapter.vendor.xabar.service;

import static uz.heeyid.smsadapter.constant.SmsAdapterExceptionStatus.SMS_ADAPTER_NOT_FOUND;
import static uz.heeyid.smsadapter.utils.XabarConstants.NAME;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import uz.heeyid.smsadapter.service.SmsService;
import uz.heeyid.smsadapter.vendor.ConditionalOnVendor;
import uz.heeyid.smsadapter.vendor.xabar.mapper.XabarMapper;
import uz.heeyid.smsadapter.vendor.xabar.model.XabarStatusRequest;
import uz.heeyid.smsadapter.vendor.xabar.property.XabarProperties;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * Xabar's delivery reports. A wrong secret is NOT_FOUND, as if the path did not exist. A report
 * without a message id or a status is skipped; the others go to {@link SmsService#report}.
 */
@Service
@ConditionalOnVendor(NAME)
public class XabarReportService {

  private final SmsService sms;
  private final XabarMapper mapper;
  private final byte[] secret;

  public XabarReportService(SmsService sms, XabarMapper mapper, XabarProperties properties) {
    this.sms = sms;
    this.mapper = mapper;
    this.secret = properties.callbackSecret().getBytes(StandardCharsets.UTF_8);
  }

  public void report(String secret, XabarStatusRequest body) {
    if (!MessageDigest.isEqual(this.secret, secret.getBytes(StandardCharsets.UTF_8))) {
      throw new GeneralException(SMS_ADAPTER_NOT_FOUND);
    }
    var reports = body.messages();
    if (reports == null) {
      return;
    }
    for (var report : reports) {
      var messageId = report.messageId();
      var status = report.status();
      if (messageId == null || status == null) {
        continue;
      }
      sms.report(
          NAME,
          messageId,
          mapper.status(status),
          status,
          mapper.parts(report.messageParts()),
          mapper.at(report.statusDate()));
    }
  }
}
