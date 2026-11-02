// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import java.util.Optional;

/** The acts kept: each whole, with its versions. */
public interface ActStore {

  Optional<Act> find(ActIdentifier id);

  Optional<Act> find(Eli eli);

  /**
   * Keeps the act, with its versions, unless it is kept already (another reader's first read got
   * there first).
   *
   * @return whether it was kept now
   */
  boolean add(Act act);
}
