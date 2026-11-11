package uz.heeyid.smsadapter.vendor;

import java.util.List;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True when the vendor of {@link ConditionalOnVendor} is active or in the failover list. The two
 * settings have no default: a missing one stops startup.
 */
class OnVendorCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var attributes = metadata.getAnnotationAttributes(ConditionalOnVendor.class.getName());
    if (attributes == null) {
      return false;
    }
    var name = String.valueOf(attributes.get("value"));
    // Required, and strict about ${SMS_ACTIVE_VENDOR}: a missing value stops startup naming it.
    var environment = context.getEnvironment();
    var active = environment.getRequiredProperty("sms.active-vendor");
    var failover = environment.getRequiredProperty("sms.failover", String[].class);
    return name.equals(active) || List.of(failover).contains(name);
  }
}
