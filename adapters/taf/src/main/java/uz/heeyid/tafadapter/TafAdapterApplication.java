package uz.heeyid.tafadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TafAdapterApplication {

  public static void main(String[] args) {
    SpringApplication.run(TafAdapterApplication.class, args);
  }
}
