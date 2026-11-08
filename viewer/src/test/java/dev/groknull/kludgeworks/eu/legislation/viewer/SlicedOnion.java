// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The rules of the sliced onion that neither Spring Modulith nor jMolecules has. {@code
 * ModularityTests} checks them.
 */
final class SlicedOnion {

  /** The application's packages: its root package, its modules and the libraries beside them. */
  static final String APPLICATION = "dev.groknull.kludgeworks.eu.legislation";

  /** The application's root package, which holds {@link ViewerApplication}. */
  private static final String ROOT = ViewerApplication.class.getPackageName();

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
   * A library uses a module only through the module's API, its root package. A library is a package
   * of the application's own code that is in no module and is not the application's root package:
   * for example {@code chrome}, {@code legaltext}, {@code comparepage} or {@code cellar}. It may
   * use a module's API types, such as the content module's {@code Element} or the language module's
   * {@code Language}. It may not use anything in a module's sub-packages.
   *
   * <p>Spring Modulith's checks do not see this. A library is outside the application's root
   * package, so Spring Modulith neither imports it nor treats it as a module. Without this rule, a
   * library is a back door: a module's web side could put code that uses another module's internals
   * into a library, and every module could then use that code.
   */
  static ArchRule librariesUseOnlyModulesApis(ApplicationModules modules) {
    return classes()
        .that(
            new DescribedPredicate<JavaClass>("are in a library") {
              @Override
              public boolean test(JavaClass type) {
                return type.getPackageName().startsWith(APPLICATION + ".")
                    && !type.getPackageName().equals(ROOT)
                    && module(modules, type) == null;
              }
            })
        .should(
            new ArchCondition<JavaClass>("use a module only through its API, its root package") {
              @Override
              public void check(JavaClass type, ConditionEvents events) {
                // One violation for each package of a module's internals that the type uses, with
                // the types it uses there, not one for each call
                SortedMap<String, SortedSet<String>> used = new TreeMap<>();
                for (var dependency : type.getDirectDependenciesFromSelf()) {
                  var target = dependency.getTargetClass().getBaseComponentType();
                  var owner = module(modules, target);
                  if (owner != null
                      && !target.getPackageName().equals(owner.getBasePackage().getName())) {
                    used.computeIfAbsent(target.getPackageName(), each -> new TreeSet<>())
                        .add(target.getSimpleName());
                  }
                }
                used.forEach(
                    (pkg, types) ->
                        events.add(
                            SimpleConditionEvent.violated(
                                type,
                                type.getName()
                                    + " uses "
                                    + String.join(", ", types)
                                    + " of "
                                    + pkg
                                    + ", which is inside a module, not its API")));
              }
            })
        .because("a library beside the modules is not a way into a module's internals");
  }

  /** The module of {@code type}, or none if it is in no module, as the application's own are. */
  private static @Nullable ApplicationModule module(ApplicationModules modules, JavaClass type) {
    return modules
        .getModuleByType(type.getName())
        .filter(each -> !each.isRootModule())
        .orElse(null);
  }
}
