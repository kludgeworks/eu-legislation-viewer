// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;

/** All acts, by their CELEX or ELI: Cellar's RDF, through {@code infra}. */
@Repository
public interface Acts {

  /** The act with this CELEX, none if Cellar has no such work. */
  Optional<Act> find(ActIdentifier id);

  /** The act this ELI names, none if Cellar knows no act by it. */
  Optional<Act> find(Eli eli);
}
