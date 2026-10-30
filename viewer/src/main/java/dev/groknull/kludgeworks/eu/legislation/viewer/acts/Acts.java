// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.time.LocalDate;
import java.util.Optional;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;

/** The acts module's API: an act by its CELEX or ELI, and the version that applies on a date. */
@ApplicationRing
@org.jmolecules.ddd.annotation.Service
@org.springframework.stereotype.Service
public class Acts {
  private final ActCatalogue catalogue;

  Acts(ActCatalogue catalogue) {
    this.catalogue = catalogue;
  }

  public Optional<Act> find(ActIdentifier id) {
    return catalogue.find(id);
  }

  public Optional<Act> find(Eli eli) {
    return catalogue.identify(eli).flatMap(catalogue::find);
  }

  /**
   * The version of the act that applies on {@code date}: none for an unknown act, or before it
   * applies.
   */
  public Optional<Version> versionOn(ActIdentifier id, LocalDate date) {
    return find(id).flatMap(act -> act.timeline().applicableOn(date));
  }
}
