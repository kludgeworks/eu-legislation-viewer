// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How many differences each act made in a comparison of two of an act's versions (Design: Acts
 * between the two versions): one version's text with another's, by their roots, attributed as its
 * sidenotes are. Counted once and kept, for the chips; an act's dropped once it is read again.
 */
@Repository
public interface StepCounts {

  /**
   * The comparison's counts for {@code act}, from the text at {@code from} to the one at {@code
   * to}.
   */
  Optional<Counted> find(ActIdentifier act, ContentHash from, ContentHash to);

  /** Keeps them, unless they are kept already (another reader counted them first). */
  void add(ActIdentifier act, ContentHash from, ContentHash to, Counted counted);

  /**
   * A comparison's counts.
   *
   * @param differences how many differences each act between the versions made, alone or with
   *     others: 0 for one that made none
   * @param together how many acts made together
   * @param unknown how many one of its acts made, not known which
   */
  @ValueObject
  record Counted(Map<ActIdentifier, Integer> differences, int together, int unknown) {
    public Counted {
      differences = Map.copyOf(differences);
    }
  }
}
