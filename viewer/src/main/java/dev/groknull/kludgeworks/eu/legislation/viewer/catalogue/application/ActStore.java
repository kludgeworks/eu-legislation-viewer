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

  /** The act whose version's text is the document {@code celex} (02015R1998-20260322). */
  Optional<Act> findByVersion(String celex);

  /**
   * Keeps the act, with its versions, unless it is kept already (another reader's first read got
   * there first).
   *
   * @return whether it was kept now
   */
  boolean add(Act act);
}
