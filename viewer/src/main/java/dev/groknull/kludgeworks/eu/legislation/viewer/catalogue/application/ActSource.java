// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import java.util.Optional;

/** Where acts come from. This is Cellar, the system of record. */
public interface ActSource {

  /** Returns the act with this CELEX, or empty if there is no such work. */
  Optional<Act> find(ActIdentifier id);

  /** Returns the act that this ELI names, or empty if no act has it. */
  Optional<Act> find(Eli eli);
}
