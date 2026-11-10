package uz.heeyid.smsadapter.config;

import static uz.heeyid.smsadapter.utils.ConstantUtils.DELIVERY_REPORTS_PREFIX;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.heeyid.smsadapter.property.SmsProperties;

/**
 * The vendors' delivery reports have a port of their own (sms.callback-port): plain HTTP, reached
 * only through the bank's ingress, which ends TLS and lets in only the vendors' addresses. A vendor
 * has no client certificate of heeyID's CA, so it cannot use the API port. Each port serves only
 * its own paths: the callback port only /delivery-reports/**, the API port everything else. A
 * delivery report also carries the vendor's secret in its path.
 */
@Configuration(proxyBeanMethods = false)
public class CallbackServerConfig {

  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> callbackConnector(
      SmsProperties properties) {
    return factory -> {
      var connector = new Connector();
      connector.setPort(properties.callbackPort());
      factory.addAdditionalConnectors(connector);
    };
  }

  @Bean
  FilterRegistrationBean<OncePerRequestFilter> portPaths(SmsProperties properties) {
    var filter =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws ServletException, IOException {
            var onCallbackPort = request.getLocalPort() == properties.callbackPort();
            var report = request.getRequestURI().startsWith(DELIVERY_REPORTS_PREFIX);
            if (onCallbackPort != report) {
              response.setStatus(HttpServletResponse.SC_NOT_FOUND);
              return;
            }
            chain.doFilter(request, response);
          }
        };
    var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
