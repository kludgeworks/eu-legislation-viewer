// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import java.util.Optional;

/** Where acts come from: Cellar, the system of record. */
public interface ActSource {

  /** The act with this CELEX, none if there is no such work. */
  Optional<Act> find(ActIdentifier id);

  /** The act this ELI names, none if no act has it. */
  Optional<Act> find(Eli eli);
}
