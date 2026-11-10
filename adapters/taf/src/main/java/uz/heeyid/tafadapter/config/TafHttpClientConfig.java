package uz.heeyid.tafadapter.config;

import java.net.http.HttpClient;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import uz.heeyid.tafadapter.client.TafAgentHttpClient;
import uz.heeyid.tafadapter.client.TafHealthHttpClient;
import uz.heeyid.tafadapter.client.TafTokenHttpClient;
import uz.heeyid.tafadapter.property.TafProperties;
import uz.khakimjanovich.platform.observability.ObservabilityConstants;

/**
 * One @HttpExchange client per TAF host: the token endpoint, the agent's API and the agent's
 * health. TAF is trusted only through its pinned CA (SAD TB-5); no body reaches the log (codes,
 * phone numbers, the PINFL); the JDK client follows no redirect. Every status comes back as an
 * answer: TafClient turns a refusal into the contract's error.
 */
@Configuration(proxyBeanMethods = false)
public class TafHttpClientConfig {

  @Bean
  TafTokenHttpClient tafTokenHttpClient(
      TafProperties properties, RestClient.Builder builder, SslBundles sslBundles) {
    return client(builder, sslBundles, properties, properties.authUrl(), TafTokenHttpClient.class);
  }

  @Bean
  TafAgentHttpClient tafAgentHttpClient(
      TafProperties properties, RestClient.Builder builder, SslBundles sslBundles) {
    return client(builder, sslBundles, properties, properties.agentUrl(), TafAgentHttpClient.class);
  }

  @Bean
  TafHealthHttpClient tafHealthHttpClient(
      TafProperties properties, RestClient.Builder builder, SslBundles sslBundles) {
    return client(
        builder, sslBundles, properties, properties.healthUrl(), TafHealthHttpClient.class);
  }

  private static <T> T client(
      RestClient.Builder builder,
      SslBundles sslBundles,
      TafProperties properties,
      String baseUrl,
      Class<T> type) {
    var http =
        HttpClient.newBuilder()
            .sslContext(sslBundles.getBundle("taf").createSslContext())
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.connectTimeout())
            .build();
    var requestFactory = new JdkClientHttpRequestFactory(http);
    requestFactory.setReadTimeout(properties.readTimeout());
    var restClient =
        builder
            .clone()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .defaultRequest(r -> r.attribute(ObservabilityConstants.NO_BODIES, true))
            // Every status is an answer for TafClient to read; none is thrown here.
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(type);
  }
}
