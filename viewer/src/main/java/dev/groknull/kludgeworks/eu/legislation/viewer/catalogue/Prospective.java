// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * The occurrences of a text that a prospective amendment of a day changes: a change that the text
 * includes, and that does not apply on the day yet. Its act applies there from a later day. {@link
 * LawOverTime#changesThatDoNotApplyYet} gives it, and {@link #at} tells whether an occurrence has
 * such a change.
 *
 * <p>For example, the text of 2018/1862 of 1 August 2022 includes the changes of 2022/1190, which
 * apply from 15 October 2026. On 1 August 2022, Article 3, point 8 is such an occurrence.
 *
 * @param day the day
 * @param byOccurrence the act of the change, by the eId of the occurrence that it changes
 */
@ValueObject
public record Prospective(LocalDate day, Map<String, ActIdentifier> byOccurrence) {

  public Prospective {
    byOccurrence = Map.copyOf(byOccurrence);
  }

  /**
   * Returns the occurrences of a text that a prospective amendment of {@code day} changes, each
   * with the act of the amendment ({@link Divergence#in}).
   *
   * @param day the day
   * @param text the tags of the text, or null if the viewer did not build it
   * @param ofTheDay the divergences of the day
   */
  static Prospective of(LocalDate day, @Nullable TextTags text, List<Divergence> ofTheDay) {
    var byOccurrence = new LinkedHashMap<String, ActIdentifier>();
    if (text != null) {
      for (var divergence : ofTheDay) {
        if (divergence.kind() == Divergence.Kind.PROSPECTIVE) {
          divergence
              .in(text.eIds())
              .forEach(occurrence -> byOccurrence.putIfAbsent(occurrence.eId(), divergence.act()));
        }
      }
    }
    return new Prospective(day, byOccurrence);
  }

  /**
   * Returns the act of a change at the occurrence {@code eId} that does not apply on the day yet
   * ({@link TheTextAComparisonShows#changesThatDoNotApplyYet}). Empty if each change there applies.
   */
  public Optional<ActIdentifier> at(String eId) {
    return TheTextAComparisonShows.changesThatDoNotApplyYet(eId, byOccurrence);
  }
}
