// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The occurrences of a text that have a change that does not apply on a day yet. The text includes
 * the change, and its act applies there from a later day. {@link Act#pending} finds them.
 *
 * <p>For example, the text of 2018/1862 of 1 August 2022 includes the changes of 2022/1190, which
 * apply from 15 October 2026. On 1 August 2022, Article 3, point 8 is such an occurrence.
 *
 * @param day the day
 * @param byOccurrence the act of the change, by the eId of the occurrence
 */
@ValueObject
public record Pending(LocalDate day, Map<String, ActIdentifier> byOccurrence) {

  public Pending {
    byOccurrence = Map.copyOf(byOccurrence);
  }

  /**
   * Returns the act of a change at the occurrence {@code eId} that does not apply on the day yet.
   * The change is at the occurrence itself, at an occurrence that the occurrence is in, or at an
   * occurrence in it. Empty if each change there applies.
   */
  public Optional<ActIdentifier> at(String eId) {
    var exact = byOccurrence.get(eId);
    if (exact != null) {
      return Optional.of(exact);
    }
    return byOccurrence.entrySet().stream()
        .filter(each -> Amended.in(eId, each.getKey()) || Amended.in(each.getKey(), eId))
        .map(Map.Entry::getValue)
        .findFirst();
  }
}
