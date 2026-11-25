// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.AmendingHistory;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.CataloguedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Histories;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.History;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheVersionsOfAnAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Gives the history of an act. It gets what the rules need and that a request does not have: the
 * act, today, and the texts that are built. It decides nothing about the law: {@link ActOverTime}
 * and {@link History} apply the rules.
 */
@Service
public class HistoryService implements Histories {
  private final Lookups lookups;
  private final Texts texts;
  private final Clock clock;

  public HistoryService(Lookups lookups, Texts texts, Clock clock) {
    this.lookups = lookups;
    this.texts = texts;
    this.clock = clock;
  }

  @Override
  public History of(
      Eli eli,
      Language language,
      @Nullable LocalDate on,
      @Nullable LocalDate from,
      @Nullable LocalDate to) {
    var act = CataloguedActs.act(lookups, eli);
    var today = today();
    var days = Days.asked(on, from, to, today);
    var history = ActOverTime.of(act, language).on(days.day(), today);
    return Optional.ofNullable(days.first())
        .map(first -> history.between(first, built(act, language)))
        .orElse(history);
  }

  @Override
  public History of(ActIdentifier id, Language language, LocalDate day) {
    return overTime(id, language).on(day, today());
  }

  @Override
  public ActOverTime overTime(ActIdentifier id, Language language) {
    return ActOverTime.of(CataloguedActs.act(lookups, id), language);
  }

  @Override
  public Optional<AmendingHistory> amending(
      Eli eli, ActIdentifier amending, Language language, @Nullable LocalDate on) {
    var act = CataloguedActs.act(lookups, eli);
    var today = today();
    return Optional.of(amending)
        .filter(ActOverTime.amendingActsOf(act, language)::contains)
        .map(each ->
            AmendingHistory.of(
                act,
                each,
                language,
                Optional.ofNullable(on).orElse(today),
                today,
                built(act, language)));
  }

  private LocalDate today() {
    return TheVersionsOfAnAct.today(clock.instant());
  }

  /** Returns the tags of the texts of {@code act} in {@code language} that are built. */
  private Map<Version, TextTags> built(Act act, Language language) {
    return texts.tags(texts.preparation(act, language));
  }

  /**
   * The days that a reader asks the history for.
   *
   * @param first the first of two days. Null for one day.
   * @param day the day. With two days, it is the second day.
   */
  private record Days(@Nullable LocalDate first, LocalDate day) {

    /**
     * Returns the days that the reader asks for: the two days {@code from} and {@code to}, in their
     * order, if the reader gives the two and they are not the same. Else the day {@code on}, or
     * {@code today} if the reader gives no day.
     */
    static Days asked(
        @Nullable LocalDate on, @Nullable LocalDate from, @Nullable LocalDate to, LocalDate today) {
      if (from == null || to == null || from.equals(to)) {
        return new Days(null, Optional.ofNullable(on).orElse(today));
      }
      return from.isBefore(to) ? new Days(from, to) : new Days(to, from);
    }
  }
}
