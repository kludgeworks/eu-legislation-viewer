// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The rule of the sliced onion that neither Spring Modulith nor jMolecules has. {@code
 * ModularityTests} checks it.
 */
final class SlicedOnion {

  private SlicedOnion() {}

  /**
   * Every package of a module says which ring it is in. jMolecules does not look for a ring in the
   * parent packages, so a package without one escapes its rules. A module may name and nest its
   * packages as it likes: which ring a package is in is its author's choice, and jMolecules checks
   * the dependencies between the rings.
   */
  static ArchRule packagesDeclareARing(ApplicationModules modules) {
    return classes()
        .that(
            new DescribedPredicate<JavaClass>("are in a module") {
              @Override
              public boolean test(JavaClass type) {
                return module(modules, type) != null;
              }
            })
        .should(
            new ArchCondition<JavaClass>("be in a package that declares its ring") {
              @Override
              public void check(JavaClass type, ConditionEvents events) {
                var pkg = type.getPackage();
                if (!pkg.isMetaAnnotatedWith(DomainRing.class)
                    && !pkg.isMetaAnnotatedWith(ApplicationRing.class)
                    && !pkg.isMetaAnnotatedWith(InfrastructureRing.class)) {
                  events.add(
                      SimpleConditionEvent.violated(
                          type,
                          type.getPackageName()
                              + " is in no ring: annotate its package-info with @DomainRing,"
                              + " @ApplicationRing or @InfrastructureRing"));
                }
              }
            })
        .because("jMolecules checks only the packages that say which ring they are in");
  }

  /**
   * The module of {@code type}, or none if it is in the application's root package. It looks the
   * module up by the name of the type, because {@code modules} knows only the classes of its own
   * import.
   */
  private static @Nullable ApplicationModule module(ApplicationModules modules, JavaClass type) {
    return modules
        .getModuleByType(type.getName())
        .filter(each -> !each.isRootModule())
        .orElse(null);
  }
}
