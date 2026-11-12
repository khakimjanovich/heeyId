package uz.heeyid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build if one module reaches into another module's internals. */
class ModularityTest {

  @Test
  void modulesRespectTheirBoundaries() {
    var modules = ApplicationModules.of(CustomerGatewayApplication.class);
    // Modulith finds modules only below the app's package: an app class moved out of uz.heeyid
    // finds none, and verify() passes on nothing.
    assertThat(modules.stream().map(module -> module.getBasePackage().getName()))
        .contains("uz.heeyid.registration", "uz.heeyid.notification");
    modules.verify();
  }
}
