// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;

/** All acts, by their CELEX or ELI. The {@code infra} package reads them from Cellar's RDF. */
@Repository
public interface Acts {

  /** Returns the act with this CELEX, or empty if Cellar has no such work. */
  Optional<Act> find(ActIdentifier id);

  /** Returns the act that this ELI names, or empty if Cellar knows no act by it. */
  Optional<Act> find(Eli eli);
}
