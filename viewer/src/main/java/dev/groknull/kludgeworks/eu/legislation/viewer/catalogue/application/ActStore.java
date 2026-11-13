// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** The stored acts, which the viewer holds. Each act is stored whole, with its versions. */
public interface ActStore {

  Optional<Act> find(ActIdentifier id);

  Optional<Act> find(Eli eli);

  /**
   * Returns the act that has a version whose text is the document {@code celex}. For example,
   * {@code 02015R1998-20260322}.
   */
  Optional<Act> findByVersion(String celex);

  /**
   * Returns the held act of each of {@code celexes} that is a held act's CELEX or the CELEX of one
   * of its versions. A CELEX that is neither is left out.
   */
  Map<String, ActIdentifier> held(Collection<String> celexes);

  /**
   * Stores the act with its versions, unless it is stored already. It is stored already if the
   * first read of another reader stored it first.
   *
   * @return whether it was stored now
   */
  boolean add(Act act);

  /**
   * Returns the stored versions of the act that the last read from Cellar did not have, by CELEX,
   * with when a read first did not have each.
   */
  Map<String, Instant> missing(ActIdentifier id);

  /**
   * Stores the act anew, as Cellar has it now, with its titles, dates and versions. A stored
   * version that Cellar no longer lists stays, as {@code missing} says, until it is withdrawn.
   *
   * @param missing the stored versions that Cellar does not list, by CELEX, with when a read first
   *     did not list each
   */
  void update(Act act, Map<String, Instant> missing);

  /** Deletes a version of the act, with the states of its texts and the steps that name it. */
  void withdraw(ActIdentifier id, String celex);
}
