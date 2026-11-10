package uz.heeyid.smsadapter.vendor;

import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** True when the vendor of {@link ConditionalOnVendor} is active or in the failover list. */
class OnVendorCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var attributes = metadata.getAnnotationAttributes(ConditionalOnVendor.class.getName());
    if (attributes == null) {
      return false;
    }
    var name = String.valueOf(attributes.get("value"));
    var binder = Binder.get(context.getEnvironment());
    var active = binder.bind("sms.active-vendor", String.class).orElse("");
    var failover = binder.bind("sms.failover", Bindable.listOf(String.class)).orElse(List.of());
    return name.equals(active) || failover.contains(name);
  }
}
