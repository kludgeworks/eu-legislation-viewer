// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Who made a difference: the acts that made it, or the acts of which one made it where it is not
 * known which. The sidenote of the difference says it in words.
 *
 * @param acts the acts that made the difference, in the order that the versions include them. The
 *     difference counts in the changes of each of these acts. Empty where no act is known.
 * @param candidates the acts of which one made the difference, where it is not known which act.
 *     Empty where the act is known, or where nothing names an act.
 */
@ValueObject
public record Attribution(List<ActIdentifier> acts, List<ActIdentifier> candidates) {

  /** No act is known to have made the difference, and no act is named as one that may have. */
  public static final Attribution NONE = new Attribution(List.of(), List.of());

  public Attribution {
    acts = List.copyOf(acts);
    candidates = List.copyOf(candidates);
  }

  /** Returns the attribution of a difference that {@code acts} made. */
  public static Attribution madeBy(List<ActIdentifier> acts) {
    return new Attribution(acts, List.of());
  }

  /**
   * Returns the attribution of a difference that one of {@code candidates} made, not known which.
   */
  public static Attribution oneOf(List<ActIdentifier> candidates) {
    return new Attribution(List.of(), candidates);
  }

  /**
   * Returns whether the difference is a correction (R-WHO-10): only corrigenda made it. A
   * difference that an amending act made together with a corrigendum is not a correction. Nor is a
   * difference that no known act made.
   */
  public boolean corrected() {
    return !acts.isEmpty() && acts.stream().allMatch(ActIdentifier::corrigendum);
  }

  /** Returns whether no act is named: neither an act that made it, nor acts of which one did. */
  public boolean unattributed() {
    return acts.isEmpty() && candidates.isEmpty();
  }
}
