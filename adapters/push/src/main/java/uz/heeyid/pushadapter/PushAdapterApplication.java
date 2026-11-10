package uz.heeyid.pushadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** The adapter. Only the adapter's own settings are scanned. */
@SpringBootApplication
@ConfigurationPropertiesScan("uz.heeyid.pushadapter.property")
public class PushAdapterApplication {

  public static void main(String[] args) {
    SpringApplication.run(PushAdapterApplication.class, args);
  }
}
