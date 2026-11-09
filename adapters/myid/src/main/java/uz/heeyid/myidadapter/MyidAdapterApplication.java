package uz.heeyid.myidadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MyidAdapterApplication {

  public static void main(String[] args) {
    SpringApplication.run(MyidAdapterApplication.class, args);
  }
}
