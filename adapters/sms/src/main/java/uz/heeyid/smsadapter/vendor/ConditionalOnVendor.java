package uz.heeyid.smsadapter.vendor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * A vendor module's beans and settings exist only when the vendor is configured: named in
 * sms.active-vendor or sms.failover. So a bank sets only the settings of the vendors it uses.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Conditional(OnVendorCondition.class)
public @interface ConditionalOnVendor {

  /** The vendor's name. */
  String value();
}
