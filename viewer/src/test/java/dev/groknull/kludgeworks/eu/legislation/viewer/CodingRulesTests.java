// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests how the application's code writes absence. Each rule is a test of its own:
 *
 * <ul>
 *   <li>A lookup that can find nothing returns an {@link Optional}.
 *   <li>A value that can be absent is {@code @Nullable}, so no field is an {@link Optional}. Record
 *       components are fields. ArchUnit cannot read {@code @Nullable}, because JSpecify's
 *       annotation is a type-use annotation.
 *   <li>No method or constructor takes an {@link Optional}. A parameter that can be absent is
 *       {@code @Nullable}, or the method has an overload without it.
 * </ul>
 */
class CodingRulesTests {

  /** The application's own code: its root package and its modules. */
  private static JavaClasses classes;

  @BeforeAll
  static void importTheApplication() {
    classes =
        new ClassFileImporter()
            .withImportOption(new ProductionClasses())
            .importPackages(ViewerApplication.class.getPackageName());
  }

  @Test
  void repositoriesFindOptionals() {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .areMetaAnnotatedWith(Repository.class)
        .and()
        .haveNameStartingWith("find")
        .should()
        .haveRawReturnType(Optional.class)
        .because("a lookup that may find nothing returns an Optional")
        .check(classes);
  }

  @Test
  void noFieldIsAnOptional() {
    fields()
        .should()
        .notHaveRawType(Optional.class)
        .because("a value that may be absent, as a record component, is @Nullable")
        .check(classes);
  }

  @Test
  void noParameterIsAnOptional() {
    codeUnits()
        .should(
            new ArchCondition<JavaCodeUnit>("not take an Optional") {
              @Override
              public void check(JavaCodeUnit unit, ConditionEvents events) {
                // The lambdas that javac compiles to synthetic methods take the values they
                // capture as parameters
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
        .because("Optional is for return values: a parameter that may be absent is @Nullable")
        .check(classes);
  }
}
