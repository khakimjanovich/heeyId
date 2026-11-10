package uz.heeyid.pushadapter.config;

import static uz.heeyid.pushadapter.utils.ConstantUtils.BREAKER_GAUGE;
import static uz.heeyid.pushadapter.utils.ConstantUtils.FIREBASE_APP_NAME;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.heeyid.pushadapter.property.FcmProperties;
import uz.heeyid.pushadapter.service.FcmBreaker;

/**
 * FCM through Google's Firebase Admin SDK: the adapter's own FirebaseApp for the bank's project,
 * with its service-account credential. A credential file that is missing or not a service account
 * stops startup.
 */
@Configuration(proxyBeanMethods = false)
public class FirebaseConfig {

  /** The HTTP client of FCM and of Google's token service; it uses the JVM's proxy. */
  @Bean
  HttpTransport fcmTransport() {
    return new NetHttpTransport();
  }

  @Bean(destroyMethod = "delete")
  FirebaseApp firebaseApp(FcmProperties properties, HttpTransport transport) throws IOException {
    ServiceAccountCredentials credentials;
    try (var json = Files.newInputStream(properties.credentialsFile())) {
      credentials = ServiceAccountCredentials.fromStream(json, () -> transport);
    }
    var options =
        FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId(properties.projectId())
            .setHttpTransport(transport)
            .setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
            .setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()))
            .build();
    return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
  }

  @Bean
  FirebaseMessaging firebaseMessaging(FirebaseApp app) {
    return FirebaseMessaging.getInstance(app);
  }

  @Bean
  FcmBreaker fcmBreaker(FcmProperties properties, Clock clock, MeterRegistry registry) {
    var breaker =
        new FcmBreaker(properties.breaker().failures(), properties.breaker().probeAfter(), clock);
    Gauge.builder(BREAKER_GAUGE, breaker, b -> b.isOpen() ? 1 : 0).register(registry);
    return breaker;
  }
}
