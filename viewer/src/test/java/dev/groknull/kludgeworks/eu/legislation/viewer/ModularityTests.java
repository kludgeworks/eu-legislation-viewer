// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The architecture's checks, by Spring Modulith: no cycles between modules, no access to another
 * module's internals, and, since jmolecules-archunit is on the classpath, jMolecules' rules too.
 * Among them the sliced onion's: in every module the domain ring depends on nothing outward and the
 * application ring only on the domain, and infrastructure (the cellar module, every {@code infra})
 * is used only from infrastructure.
 */
class ModularityTests {

  @Test
  void modulesRespectTheirBoundaries() {
    ApplicationModules.of(ViewerApplication.class).verify();
  }
}
