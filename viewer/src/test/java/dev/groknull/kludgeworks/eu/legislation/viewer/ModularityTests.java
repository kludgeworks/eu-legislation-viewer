// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Tests the modules and the sliced onion. Each rule is a test of its own, so a failing test names
 * the rule that is broken.
 *
 * <ul>
 *   <li>Spring Modulith: there are no cycles between modules, and no module uses the internals of
 *       another module. A module's {@code infra} and {@code web} are internals, so they are its
 *       own.
 *   <li>jMolecules, which is on the classpath, so Spring Modulith runs its rules too: in every
 *       module, the domain ring depends on nothing outward, the application ring only on the
 *       domain, and only infrastructure uses infrastructure.
 *   <li>A rule of our own that neither has, in {@link SlicedOnion}: every package of a module says
 *       which ring it is in.
 * </ul>
 */
class ModularityTests {

  private static ApplicationModules modules;

  /**
   * The application's own code, imported by ArchUnit, for the rule of our own. An ArchUnit rule
   * checks a set of classes that ArchUnit imported, which {@code modules} does not give out.
   */
  private static JavaClasses classes;

  @BeforeAll
  static void importTheApplication() {
    modules = ApplicationModules.of(ViewerApplication.class, new ProductionClasses());
    classes =
        new ClassFileImporter()
            .withImportOption(new ProductionClasses())
            .importPackages(ViewerApplication.class.getPackageName());
  }

  @Test
  void modulesRespectTheirBoundaries() {
    // The default verifications, which include the rules of jMolecules
    modules.verify();
  }

  @Test
  void packagesDeclareARing() {
    SlicedOnion.packagesDeclareARing(modules).check(classes);
  }

  @Test
  void templatesUseOnlyTheirModule() {
    // Our templates only, those of src/main/jte: jte-models' jar has templates of its own, in the
    // same package
    var templates =
        new ClassFileImporter()
            .withImportOption(location -> location.contains("/src/main/jte/"))
            .importPackages(SlicedOnion.TEMPLATES);
    SlicedOnion.templatesUseOnlyTheirModule(modules).check(templates);
  }
}
