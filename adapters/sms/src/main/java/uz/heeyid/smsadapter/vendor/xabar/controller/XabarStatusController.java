package uz.heeyid.smsadapter.vendor.xabar.controller;

import static uz.heeyid.smsadapter.utils.XabarConstants.NAME;
import static uz.heeyid.smsadapter.utils.XabarConstants.STATUS_PATH;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uz.heeyid.smsadapter.vendor.ConditionalOnVendor;
import uz.heeyid.smsadapter.vendor.xabar.model.XabarStatusRequest;
import uz.heeyid.smsadapter.vendor.xabar.service.XabarReportService;

/** Xabar's status push (section 2.2), on the callback port only. */
@RestController
@RequiredArgsConstructor
@ConditionalOnVendor(NAME)
public class XabarStatusController {

  private final XabarReportService reports;

  @PostMapping(STATUS_PATH)
  public ResponseEntity<Void> status(
      @PathVariable("secret") String secret, @RequestBody XabarStatusRequest body) {
    reports.report(secret, body);
    return ResponseEntity.ok().build();
  }
}
