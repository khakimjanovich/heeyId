package uz.heeyid.smsadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The adapter. Only the adapter's own settings are scanned; a vendor's settings are bound by that
 * vendor's configuration, and only when the vendor is configured (sms.active-vendor, sms.failover).
 */
@SpringBootApplication
@ConfigurationPropertiesScan("uz.heeyid.smsadapter.property")
public class SmsAdapterApplication {

  public static void main(String[] args) {
    SpringApplication.run(SmsAdapterApplication.class, args);
  }
}
