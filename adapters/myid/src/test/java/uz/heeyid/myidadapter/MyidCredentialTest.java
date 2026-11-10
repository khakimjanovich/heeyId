package uz.heeyid.myidadapter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import uz.heeyid.myidadapter.property.MyidProperties.Credential;

/** Every part of the MyID credential is required: a missing one stops startup. */
class MyidCredentialTest {

  @Test
  void aMissingLoginIsRefused() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(new Credential("client", "secret", "login", "password")))
          .isEmpty();
      assertThat(validator.validate(new Credential("client", "secret", "", "")))
          .extracting(v -> v.getPropertyPath().toString())
          .containsExactlyInAnyOrder("username", "password");
    }
  }
}
