package uz.heeyid.myidadapter.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import uz.heeyid.myidadapter.client.MyidCompareHttpClient;
import uz.heeyid.myidadapter.client.MyidInplaceHttpClient;
import uz.heeyid.myidadapter.client.MyidLongHttpClient;
import uz.heeyid.myidadapter.client.MyidMediaHttpClient;
import uz.heeyid.myidadapter.client.MyidSdkHttpClient;
import uz.heeyid.myidadapter.client.MyidTokenHttpClient;
import uz.heeyid.myidadapter.client.MyidValidationHttpClient;
import uz.heeyid.myidadapter.client.MyidWebHttpClient;
import uz.heeyid.myidadapter.property.MyidProperties;
import uz.khakimjanovich.platform.observability.ObservabilityConstants;

/**
 * One @HttpExchange client per MyID area, over three RestClients: the main host (MyID answers in 5
 * s), the main host for the calls it answers in up to 30 s, and the media host. MyID is trusted
 * only through its pinned CA (SAD TB-5); no body reaches the log (codes, photos, personal data);
 * the JDK client follows no redirect. Every status comes back as an answer: MyidClient turns a
 * refusal into the contract's error.
 */
@Configuration(proxyBeanMethods = false)
public class MyidHttpClientConfig {

  @Bean
  HttpServiceProxyFactory myidProxyFactory(
      MyidProperties properties, RestClient.Builder builder, SslBundles sslBundles) {
    return factory(builder, sslBundles, properties, properties.baseUrl(), properties.readTimeout());
  }

  @Bean
  HttpServiceProxyFactory myidLongProxyFactory(
      MyidProperties properties, RestClient.Builder builder, SslBundles sslBundles) {
    return factory(
        builder, sslBundles, properties, properties.baseUrl(), properties.longReadTimeout());
  }

  @Bean
  HttpServiceProxyFactory myidMediaProxyFactory(
      MyidProperties properties, RestClient.Builder builder, SslBundles sslBundles) {
    return factory(
        builder, sslBundles, properties, properties.mediaUrl(), properties.readTimeout());
  }

  @Bean
  MyidTokenHttpClient myidTokenHttpClient(HttpServiceProxyFactory myidProxyFactory) {
    return myidProxyFactory.createClient(MyidTokenHttpClient.class);
  }

  @Bean
  MyidSdkHttpClient myidSdkHttpClient(HttpServiceProxyFactory myidProxyFactory) {
    return myidProxyFactory.createClient(MyidSdkHttpClient.class);
  }

  @Bean
  MyidInplaceHttpClient myidInplaceHttpClient(HttpServiceProxyFactory myidProxyFactory) {
    return myidProxyFactory.createClient(MyidInplaceHttpClient.class);
  }

  @Bean
  MyidWebHttpClient myidWebHttpClient(HttpServiceProxyFactory myidProxyFactory) {
    return myidProxyFactory.createClient(MyidWebHttpClient.class);
  }

  @Bean
  MyidCompareHttpClient myidCompareHttpClient(HttpServiceProxyFactory myidProxyFactory) {
    return myidProxyFactory.createClient(MyidCompareHttpClient.class);
  }

  @Bean
  MyidValidationHttpClient myidValidationHttpClient(HttpServiceProxyFactory myidProxyFactory) {
    return myidProxyFactory.createClient(MyidValidationHttpClient.class);
  }

  @Bean
  MyidLongHttpClient myidLongHttpClient(HttpServiceProxyFactory myidLongProxyFactory) {
    return myidLongProxyFactory.createClient(MyidLongHttpClient.class);
  }

  @Bean
  MyidMediaHttpClient myidMediaHttpClient(HttpServiceProxyFactory myidMediaProxyFactory) {
    return myidMediaProxyFactory.createClient(MyidMediaHttpClient.class);
  }

  private static HttpServiceProxyFactory factory(
      RestClient.Builder builder,
      SslBundles sslBundles,
      MyidProperties properties,
      String baseUrl,
      Duration readTimeout) {
    var http =
        HttpClient.newBuilder()
            .sslContext(sslBundles.getBundle("myid").createSslContext())
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.connectTimeout())
            .build();
    var requestFactory = new JdkClientHttpRequestFactory(http);
    requestFactory.setReadTimeout(readTimeout);
    var restClient =
        builder
            .clone()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .defaultRequest(r -> r.attribute(ObservabilityConstants.NO_BODIES, true))
            // Every status is an answer for MyidClient to read; none is thrown here.
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
  }
}
