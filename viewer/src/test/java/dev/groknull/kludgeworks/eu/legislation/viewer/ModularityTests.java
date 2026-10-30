// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The architecture's checks, by Spring Modulith: no cycles between modules, no access to another
 * module's internals, and, since jmolecules-archunit is on the classpath, jMolecules' rules too.
 * Among them the sliced onion's: in every module the domain ring depends on nothing outward and the
 * application ring only on the domain, and infrastructure (the cellar module, every {@code infra})
 * is used only from infrastructure. The Formex converter is infrastructure too, but a component of
 * its own, without the viewer's rings: its rule is explicit.
 */
class ModularityTests {

  @Test
  void modulesRespectTheirBoundaries() {
    ApplicationModules.of(ViewerApplication.class).verify();
  }

  @Test
  void onlyInfrastructureUsesTheConverter() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackagesOf(ViewerApplication.class);

    noClasses()
        .that()
        .resideOutsideOfPackage("..infra..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("dev.groknull.kludgeworks.formex.converter..")
        .because("the converter is infrastructure, as the cellar module is")
        .check(classes);
  }
}
