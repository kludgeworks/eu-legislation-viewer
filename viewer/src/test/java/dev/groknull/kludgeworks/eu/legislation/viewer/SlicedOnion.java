// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.Set;
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

  /** The package jte compiles the templates into: a template's folder is a package below it. */
  static final String TEMPLATES = "gg.jte.generated.precompiled";

  /**
   * The folders of templates whose name is not their module's. Any other folder belongs to the
   * module of its name.
   */
  private static final Map<String, String> FOLDERS =
      Map.of("layout", "chrome", "legal", "legaltext", "legalpart", "legaltext");

  /**
   * The templates a module publishes, as its API is: any template may call them. The chrome
   * module's page is the shell of every page.
   */
  private static final Set<String> PUBLISHED = Set.of(TEMPLATES + ".layout.JtepageGenerated");

  /**
   * A template belongs to the module that owns its folder, and uses only that module: its packages,
   * its templates, and any module's API (its root package). It is the module's code the Java rules
   * can't see, because jte compiles every template into one package, {@link #TEMPLATES}. The rule
   * checks the compiled templates, so it sees what a template uses without naming it: a type a
   * method returns, and a template it calls.
   */
  static ArchRule templatesUseOnlyTheirModule(ApplicationModules modules) {
    return classes()
        .that()
        .resideInAPackage(TEMPLATES + "..")
        .and(
            new DescribedPredicate<JavaClass>("are templates") {
              @Override
              public boolean test(JavaClass type) {
                // The facade of jte-models (Templates, StaticTemplates, DynamicTemplates) is in the
                // root package, with the templates of the root folder, which are named
                // JteXGenerated
                return !type.getPackageName().equals(TEMPLATES)
                    || type.getSimpleName().matches("Jte.*Generated.*");
              }
            })
        .should(
            new ArchCondition<JavaClass>("use only their module's types and templates") {
              @Override
              public void check(JavaClass template, ConditionEvents events) {
                var owner = owner(modules, template.getPackageName());
                if (owner == null) {
                  events.add(
                      SimpleConditionEvent.violated(
                          template,
                          template.getName()
                              + " is in a folder that no module owns: put it in its module's"
                              + " folder, named as the module"));
                  return;
                }
                for (var dependency : template.getDirectDependenciesFromSelf()) {
                  var target = dependency.getTargetClass().getBaseComponentType();
                  var used = used(modules, target);
                  if (used != null && !used.equals(owner)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            template,
                            owner.getIdentifier()
                                + "'s template uses "
                                + used.getIdentifier()
                                + "'s "
                                + (target.getPackageName().startsWith(TEMPLATES + ".")
                                    ? "template: "
                                    : "internals: ")
                                + dependency.getDescription()));
                  }
                }
              }
            })
        .because("jte compiles every template into one package, so Spring Modulith can't see them");
  }

  /**
   * The module whose internals or template {@code type} is, or none if it is open to every
   * template: a type outside the modules, or in a module's API.
   */
  private static @Nullable ApplicationModule used(ApplicationModules modules, JavaClass type) {
    var pkg = type.getPackageName();
    if (pkg.startsWith(TEMPLATES + ".")) {
      return PUBLISHED.contains(type.getName()) ? null : owner(modules, pkg);
    }
    var module = module(modules, type);
    return module == null || module.getBasePackage().getName().equals(pkg) ? null : module;
  }

  /** The module that owns the templates of {@code pkg}, below {@link #TEMPLATES}, if any. */
  private static @Nullable ApplicationModule owner(ApplicationModules modules, String pkg) {
    if (!pkg.startsWith(TEMPLATES + ".")) {
      return null;
    }
    var folder = pkg.substring(TEMPLATES.length() + 1).split("\\.", 2)[0];
    return modules.getModuleByName(FOLDERS.getOrDefault(folder, folder)).orElse(null);
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
