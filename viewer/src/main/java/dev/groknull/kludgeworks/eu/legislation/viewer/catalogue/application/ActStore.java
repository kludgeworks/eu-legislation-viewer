// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** The stored acts. Each act is stored whole, with its versions. */
public interface ActStore {

  Optional<Act> find(ActIdentifier id);

  Optional<Act> find(Eli eli);

  /**
   * Returns the act that has a version whose text is the document {@code celex}. For example,
   * {@code 02015R1998-20260322}.
   */
  Optional<Act> findByVersion(String celex);

  /**
   * Stores the act with its versions, unless it is stored already. It is stored already if the
   * first read of another reader stored it first.
   *
   * @return whether it was stored now
   */
  boolean add(Act act);

  /**
   * Claims the act's refresh, if it was last read from Cellar more than {@code after} ago. It marks
   * the act as read now, so that only one opening of it, on any instance, refreshes it.
   *
   * @return whether it was due
   */
  boolean due(ActIdentifier id, Duration after);

  /**
   * Stores the act anew, as Cellar has it now, with its titles, dates and versions. A stored
   * version that Cellar no longer lists stays, because its texts may refer to it.
   *
   * @return the versions that it did not have, in date order
   */
  List<Version> update(Act act);
}
