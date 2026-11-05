// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.time.Duration;
import java.util.List;
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

  /**
   * Claims the act's refresh, if it was last read from Cellar longer than {@code after} ago: marks
   * it read now, so that one opening of it, on any instance, refreshes it.
   *
   * @return whether it was due
   */
  boolean due(ActIdentifier id, Duration after);

  /**
   * Keeps the act anew, as Cellar has it now: its titles, dates and versions; a version kept that
   * Cellar no longer lists stays, as its texts may refer to it.
   *
   * @return the versions it didn't have, in date order
   */
  List<Version> update(Act act);
}
