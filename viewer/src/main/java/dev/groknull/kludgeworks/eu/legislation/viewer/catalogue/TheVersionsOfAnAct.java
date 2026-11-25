// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The feature "The versions of an act": one function for each of its rules. A rule reads facts and
 * gives one new fact.
 */
public final class TheVersionsOfAnAct {
  private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

  private TheVersionsOfAnAct() {}

  /**
   * The original text of an act is its first version. A consolidation that starts on the day of the
   * original text replaces it only in the languages that the consolidation has: for the other
   * languages, the act keeps its original text.
   *
   * <p>A consolidation can start before the original text: a corrigendum published before the entry
   * into force, for example 2020/1812. Then the original text starts from the date of the document,
   * if that is before the consolidation, so that a reader can compare the act as published with it.
   * If not, the act has no original text.
   *
   * @param id the act
   * @param originalFrom the day from which the original text starts
   * @param dateOfDocument the date of the document. Null if Cellar does not give it.
   * @param consolidations the consolidated versions of the act, in the order of their dates
   * @param texts the Formex of the act as published, by language
   * @return the original text as a version, or empty if the act has none
   */
  @BusinessRule(group = RuleGroup.THE_VERSIONS_OF_AN_ACT, name = "the-original-text-of-an-act")
  public static Optional<Version> theOriginalTextOfAnAct(
      ActIdentifier id,
      LocalDate originalFrom,
      @Nullable LocalDate dateOfDocument,
      List<Version> consolidations,
      Map<Language, Version.Formex> texts) {
    var onTheDay = consolidations.stream().filter(v -> v.date().equals(originalFrom)).toList();
    if (!onTheDay.isEmpty()) {
      var remaining = new EnumMap<Language, Version.Formex>(Language.class);
      remaining.putAll(texts);
      onTheDay.forEach(v -> remaining.keySet().removeAll(v.languages()));
      return remaining.isEmpty()
          ? Optional.empty()
          : Optional.of(Version.original(originalFrom, id, remaining.keySet(), remaining));
    }
    var from =
        !consolidations.isEmpty()
                && consolidations.getFirst().date().isBefore(originalFrom)
                && dateOfDocument != null
            ? dateOfDocument
            : originalFrom;
    return consolidations.isEmpty() || consolidations.getFirst().date().isAfter(from)
        ? Optional.of(Version.original(from, id, texts.keySet(), texts))
        : Optional.empty();
  }

  /**
   * Today is the day in Brussels: the EU adopts its acts there, and the Official Journal dates them
   * there. So near midnight, today can be another day for the reader.
   *
   * @param now the instant, from the clock
   */
  @BusinessRule(group = RuleGroup.THE_VERSIONS_OF_AN_ACT, name = "today-is-the-day-in-brussels")
  public static LocalDate today(Instant now) {
    return LocalDate.ofInstant(now, BRUSSELS);
  }
}
