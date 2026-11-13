// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How many differences each act made in a comparison of two versions of an act. The comparison is
 * of the text of one version with the text of another, by the texts in the content module. Each
 * difference is attributed to acts as its sidenote is. The counts are made once and stored, for the
 * chips. The counts of an act are dropped when the act is read again, and the counts of a text when
 * the text is replaced.
 */
@Repository
public interface StepCounts {

  /**
   * Returns the comparison's counts for {@code act}, from the text at {@code from} to the text at
   * {@code to}.
   */
  Optional<Counted> find(ActIdentifier act, TextId from, TextId to);

  /**
   * Stores the counts, unless they are stored already because another reader counted them first.
   */
  void add(ActIdentifier act, TextId from, TextId to, Counted counted);

  /**
   * Removes the counts of every comparison of {@code text}, from it or to it.
   *
   * @return how many comparisons' counts were removed
   */
  int remove(TextId text);

  /**
   * A comparison's counts.
   *
   * @param differences how many differences each act between the versions made, alone or with
   *     others. It is 0 for an act that made none.
   * @param together how many differences more than one act made together
   * @param unknown how many differences one of its acts made, where it is not known which act
   */
  @ValueObject
  record Counted(Map<ActIdentifier, Integer> differences, int together, int unknown) {
    public Counted {
      differences = Map.copyOf(differences);
    }
  }
}
