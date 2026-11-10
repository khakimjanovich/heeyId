package uz.heeyid.myidadapter;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * A constant (a static final field) lives in a {@code ConstantUtils} or {@code *Constants} class in
 * {@code utils}, as in the other adapters; an enum's own values and Lombok's log field are the
 * exceptions. A constant class holds only public constants.
 */
class ConstantsTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("uz.heeyid.myidadapter");

  /** {@code ConstantUtils} or a {@code *Constants} class in {@code utils}. */
  private static final DescribedPredicate<JavaClass> CONSTANT_CLASS =
      describe(
          "a ConstantUtils or *Constants class in utils",
          c ->
              c.getPackageName().endsWith(".utils")
                  && (c.getSimpleName().equals("ConstantUtils")
                      || c.getSimpleName().endsWith("Constants")));

  @Test
  void constantsLiveInConstantClasses() {
    fields()
        .that()
        .areStatic()
        .and()
        .areFinal()
        .and()
        .doNotHaveRawType(Logger.class)
        .and()
        .areDeclaredInClassesThat()
        .areNotEnums()
        .should()
        .beDeclaredInClassesThat(CONSTANT_CLASS)
        .because("constants are kept apart from the code that uses them, as in the other adapters")
        .check(CLASSES);
  }

  @Test
  void constantClassesHoldOnlyConstants() {
    fields()
        .that()
        .areDeclaredInClassesThat(CONSTANT_CLASS)
        .should()
        .beStatic()
        .andShould()
        .beFinal()
        .andShould()
        .bePublic()
        .check(CLASSES);
  }
}
