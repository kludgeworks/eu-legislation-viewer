// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import java.io.Serial;

/** Says that no act has the ELI. A page answers with 404 and the page that says so. */
final class NoSuchAct extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  /** The path of the ELI. */
  private final String eli;

  NoSuchAct(Eli eli) {
    super("No act has the ELI " + eli.path(), null, false, false);
    this.eli = eli.path();
  }

  Eli eli() {
    return new Eli(eli);
  }
}
