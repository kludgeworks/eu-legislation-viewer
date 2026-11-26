// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a comparison of the law on two days covers, as the rules give it: the amending acts between
 * the two texts, and the history of the one provision that the comparison is limited to. {@link
 * Histories} gives it. A page only puts it into words.
 *
 * @param overTime what the rules tell of the act for each day
 * @param acts the amending acts between the two texts. For one provision, the acts between them
 *     that changed the provision, oldest first.
 * @param provision the history of the provision on the later day. Null if the comparison is of the
 *     whole text, or if the later text is not built.
 */
public record ComparedDays(
    ActOverTime overTime, List<ActIdentifier> acts, @Nullable ProvisionOnADay provision) {

  public ComparedDays {
    acts = List.copyOf(acts);
  }

  /**
   * Returns what the comparison of the texts of {@code from} and {@code to} covers.
   *
   * @param occurrence the eId of the provision that the comparison is limited to, or null
   * @param toDay the later day of the comparison
   * @param texts the tags of the built texts in the language, by version
   */
  public static ComparedDays of(
      ActOverTime overTime,
      Version from,
      Version to,
      @Nullable String occurrence,
      LocalDate toDay,
      Map<Version, TextTags> texts) {
    var amending = overTime.amendingActs().stream().map(ActOverTime.Amending::act).toList();
    var between =
        overTime.act().inclusions(overTime.language(), texts).between(from, to).stream()
            .filter(amending::contains)
            .toList();
    var text = occurrence == null ? null : texts.get(to);
    if (text == null || occurrence == null) {
      return new ComparedDays(overTime, between, null);
    }
    var provision = ProvisionOnADay.of(overTime, to, occurrence, text, toDay, texts);
    return new ComparedDays(
        overTime,
        TheHistoryOfAnAct.actsOfAProvisionBetweenTwoDays(provision.history(), between).reversed(),
        provision);
  }
}
