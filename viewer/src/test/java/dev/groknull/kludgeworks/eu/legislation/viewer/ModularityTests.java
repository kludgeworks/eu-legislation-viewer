// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.VerificationOptions;

/**
 * Tests the architecture with the checks of Spring Modulith. There are no cycles between modules,
 * and no module uses the internals of another module.
 *
 * <p>jmolecules-archunit is on the classpath, so the rules of jMolecules apply too. These include
 * the rules of the sliced onion:
 *
 * <ul>
 *   <li>In every module, the domain ring depends on nothing outward.
 *   <li>The application ring depends only on the domain.
 *   <li>Only infrastructure uses infrastructure. Infrastructure is the cellar module and every
 *       {@code infra} package.
 * </ul>
 *
 * <p>Two more rules say how to write absence:
 *
 * <ul>
 *   <li>A lookup that can find nothing returns an {@link Optional}.
 *   <li>A value that can be absent is {@code @Nullable}, so no field is an {@link Optional}. Record
 *       components are fields. ArchUnit cannot read {@code @Nullable}, because JSpecify's
 *       annotation is a type-use annotation.
 *   <li>No method or constructor takes an {@link Optional}. A parameter that can be absent is
 *       {@code @Nullable}, or the method has an overload without it. The lambdas that javac
 *       compiles to synthetic methods are not checked, because their parameters are the values they
 *       capture.
 * </ul>
 */
class ModularityTests {

  static final ArchRule NO_OPTIONAL_FIELDS =
      fields()
          .should()
          .notHaveRawType(Optional.class)
          .because("a value that may be absent, as a record component, is @Nullable");

  static final ArchRule NO_OPTIONAL_PARAMETERS =
      codeUnits()
          .should(
              new ArchCondition<JavaCodeUnit>("not take an Optional") {
                @Override
                public void check(JavaCodeUnit unit, ConditionEvents events) {
                  if (unit.getModifiers().contains(JavaModifier.SYNTHETIC)) {
                    return;
                  }
                  for (var type : unit.getRawParameterTypes()) {
                    if (type.isEquivalentTo(Optional.class)) {
                      events.add(
                          SimpleConditionEvent.violated(
                              unit, unit.getFullName() + " takes an Optional"));
                    }
                  }
                }
              })
          .because("Optional is for return values: a parameter that may be absent is @Nullable");

  static final ArchRule REPOSITORIES_FIND_OPTIONALS =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areMetaAnnotatedWith(Repository.class)
          .and()
          .haveNameStartingWith("find")
          .should()
          .haveRawReturnType(Optional.class)
          .because("a lookup that may find nothing returns an Optional");

  @Test
  void modulesRespectTheirBoundaries() {
    // Use "and", not "with". "with" would replace the default verifications, which include the
    // rules of jMolecules.
    ApplicationModules.of(ViewerApplication.class, new ProductionClasses())
        .verify(
            VerificationOptions.defaults()
                .andAdditionalVerifications(
                    NO_OPTIONAL_FIELDS, NO_OPTIONAL_PARAMETERS, REPOSITORIES_FIND_OPTIONALS));
  }
}
