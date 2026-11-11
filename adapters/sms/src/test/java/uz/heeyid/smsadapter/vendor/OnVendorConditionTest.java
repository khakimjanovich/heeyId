package uz.heeyid.smsadapter.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/** No default: a missing sms.active-vendor or sms.failover stops startup, naming the variable. */
class OnVendorConditionTest {

  static class Config {
    @Bean
    @ConditionalOnVendor("xabar")
    String xabar() {
      return "xabar";
    }
  }

  final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Test
  void aMissingActiveVendorStopsStartup() {
    runner
        .withPropertyValues("sms.active-vendor=${SMS_ACTIVE_VENDOR}", "sms.failover=")
        .run(c -> assertThat(c).getFailure().hasStackTraceContaining("SMS_ACTIVE_VENDOR"));
  }

  @Test
  void aMissingFailoverStopsStartup() {
    runner
        .withPropertyValues("sms.active-vendor=xabar", "sms.failover=${SMS_FAILOVER_VENDORS}")
        .run(c -> assertThat(c).getFailure().hasStackTraceContaining("SMS_FAILOVER_VENDORS"));
  }

  @Test
  void anEmptyFailoverIsNoFailover() {
    runner
        .withPropertyValues("sms.active-vendor=xabar", "sms.failover=")
        .run(c -> assertThat(c).hasBean("xabar"));
  }
}
