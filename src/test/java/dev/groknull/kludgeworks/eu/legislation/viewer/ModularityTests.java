// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Spring Modulith's checks: no cycles between modules, no access to another module's internals. */
class ModularityTests {

  @Test
  void modulesRespectTheirBoundaries() {
    ApplicationModules.of(ViewerApplication.class).verify();
  }
}
