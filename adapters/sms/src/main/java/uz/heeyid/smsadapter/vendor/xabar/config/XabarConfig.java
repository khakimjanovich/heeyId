package uz.heeyid.smsadapter.vendor.xabar.config;

import static uz.heeyid.smsadapter.utils.XabarConstants.NAME;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import uz.heeyid.smsadapter.vendor.ConditionalOnVendor;
import uz.heeyid.smsadapter.vendor.xabar.client.XabarHttpClient;
import uz.heeyid.smsadapter.vendor.xabar.client.XabarVendor;
import uz.heeyid.smsadapter.vendor.xabar.mapper.XabarMapper;
import uz.heeyid.smsadapter.vendor.xabar.property.XabarProperties;
import uz.khakimjanovich.platform.observability.ObservabilityConstants;

/**
 * The Xabar vendor, when xabar is configured. Xabar is reached over the JVM's trust store (a public
 * CA), with the adapter's Basic login. No body reaches the log (it holds codes and phone numbers);
 * the JDK client follows no redirect. Every status comes back as an answer: XabarVendor reads it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnVendor(NAME)
@EnableConfigurationProperties(XabarProperties.class)
public class XabarConfig {

  @Bean
  XabarHttpClient xabarHttpClient(XabarProperties properties, RestClient.Builder builder) {
    var http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.connectTimeout())
            .build();
    var requestFactory = new JdkClientHttpRequestFactory(http);
    requestFactory.setReadTimeout(properties.readTimeout());
    var restClient =
        builder
            .clone()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .requestInterceptor(
                new BasicAuthenticationInterceptor(properties.username(), properties.password()))
            .defaultRequest(r -> r.attribute(ObservabilityConstants.NO_BODIES, true))
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(XabarHttpClient.class);
  }

  @Bean
  XabarVendor xabarVendor(XabarHttpClient http, XabarMapper mapper, XabarProperties properties) {
    return new XabarVendor(http, mapper, properties);
  }
}
