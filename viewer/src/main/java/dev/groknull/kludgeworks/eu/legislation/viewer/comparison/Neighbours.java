// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The feature "The neighbours of a difference": one function for each of its rules. A rule reads
 * facts and gives one new fact. It does not call another rule. A neighbour is an occurrence near a
 * difference that is the same in both texts. It has its tags as each occurrence does ({@link
 * WhoMadeAChange#tagsOfAnOccurrence}).
 */
public final class Neighbours {

  private Neighbours() {}

  /**
   * Beside a neighbour, the Synopsis says what last made it as it is. That is the act that its tags
   * name and that the texts include last, up to the later text. A corrigendum corrects and does not
   * change the act, so it does not count. If no text up to the later text includes such an act, the
   * neighbour is as in the original act.
   *
   * @param tags the tags of the neighbour
   * @param firstIncluded for each act of the tags, the day of the first text up to the later text
   *     that includes it. An act that no such text includes has no day.
   * @return the tag of the act that last changed the neighbour, or empty if it is as in the
   *     original act
   */
  @BusinessRule(group = RuleGroup.NEIGHBOURS, name = "last-change-of-a-neighbour")
  public static Optional<Tag> lastChangeOfANeighbour(
      List<Tag> tags, Map<ActIdentifier, LocalDate> firstIncluded) {
    @Nullable Tag last = null;
    @Nullable LocalDate integrated = null;
    for (var tag : tags) {
      var each = new ActIdentifier(tag.act());
      var on = firstIncluded.get(each);
      if (!each.corrigendum() && on != null && (integrated == null || on.isAfter(integrated))) {
        last = tag;
        integrated = on;
      }
    }
    return Optional.ofNullable(last);
  }
}
