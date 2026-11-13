// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A work that Cellar changed: an act, a corrigendum or a consolidation, or one of its expressions.
 *
 * @param celex the work's CELEX, for example {@code 02015R1998-20260322} or {@code 32026R0449}
 * @param targets the CELEX of each act that the work is based on (a consolidation), amends or
 *     corrects
 * @param modified when Cellar changed it ({@code cmr:lastModificationDate}), by Cellar's clock
 */
public record CellarChange(String celex, Set<String> targets, Instant modified) {

  public CellarChange {
    targets = Set.copyOf(targets);
  }

  /** Returns the CELEX of the work and of its targets. */
  Set<String> celexes() {
    var all = new HashSet<>(targets);
    all.add(celex);
    return all;
  }

  /**
   * Returns the held acts that {@code changes} touch, each with the date of its latest change. A
   * change touches an act when its work, or one of its targets, is the act or one of the act's
   * versions, as {@code held} maps each such CELEX to its act. For example:
   *
   * <ul>
   *   <li>a consolidation of a held act that the catalogue has: the work is a held version;
   *   <li>a new consolidation: its target, the act it is based on, is held;
   *   <li>a new amending act or corrigendum: the act it amends or corrects is held;
   *   <li>a republished original text: the work is the held act.
   * </ul>
   *
   * <p>A change that touches no held act is left out.
   */
  static Map<ActIdentifier, Instant> byHeldAct(
      Collection<CellarChange> changes, Map<String, ActIdentifier> held) {
    var touched = new HashMap<ActIdentifier, Instant>();
    for (var change : changes) {
      for (var celex : change.celexes()) {
        var act = held.get(celex);
        if (act != null) {
          touched.merge(act, change.modified(), (a, b) -> a.isAfter(b) ? a : b);
        }
      }
    }
    return touched;
  }
}
