package uz.heeyid.smsadapter.config;

import static uz.heeyid.smsadapter.utils.ConstantUtils.BREAKER_GAUGE;
import static uz.heeyid.smsadapter.utils.ConstantUtils.BREAKER_TAG;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.heeyid.smsadapter.property.SmsProperties;
import uz.heeyid.smsadapter.vendor.SmsVendor;
import uz.heeyid.smsadapter.vendor.VendorBreaker;
import uz.heeyid.smsadapter.vendor.Vendors;

/**
 * The vendors in the order they are tried: sms.active-vendor, then sms.failover. A name no vendor
 * module has, or a name listed twice, stops startup. Each vendor gets a breaker and its gauge
 * sms.vendor.breaker.open{vendor} (1 open, 0 closed), which drives the alert "dependency down".
 */
@Configuration(proxyBeanMethods = false)
public class VendorConfig {

  @Bean
  Vendors vendors(
      SmsProperties properties, List<SmsVendor> modules, Clock clock, MeterRegistry registry) {
    var names = new ArrayList<String>();
    names.add(properties.activeVendor());
    names.addAll(properties.failover());
    if (new LinkedHashSet<>(names).size() != names.size()) {
      throw new IllegalStateException(
          "sms.active-vendor and sms.failover name a vendor twice: " + names);
    }
    var known = modules.stream().collect(Collectors.toMap(SmsVendor::name, v -> v));
    var ordered = new ArrayList<Vendors.Entry>();
    for (var name : names) {
      var vendor = known.get(name);
      if (vendor == null) {
        throw new IllegalStateException(
            "SMS vendor '" + name + "' is not known; the known vendors are " + known.keySet());
      }
      var breaker =
          new VendorBreaker(
              properties.breaker().failures(), properties.breaker().probeAfter(), clock);
      Gauge.builder(BREAKER_GAUGE, breaker, b -> b.isOpen() ? 1 : 0)
          .tag(BREAKER_TAG, name)
          .description("1 while the vendor's breaker is open")
          .register(registry);
      ordered.add(new Vendors.Entry(vendor, breaker));
    }
    return new Vendors(List.copyOf(ordered));
  }
}
