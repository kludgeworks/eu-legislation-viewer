// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The Reading card of an act's page (Design: Mode: Read): the version being read, by its CELEX, its
 * period, the acts that made it; the date asked, when it isn't the version's first day; where the
 * text lags the law (Design: Dates); and a form asking for the version on another date.
 *
 * @param celex the version's CELEX
 * @param eurLex the version on EUR-Lex, in the page's language
 * @param period when it applied or applies: Applied 15 March 2021 to 30 December 2021
 * @param made what made it: made by 2019/103, 2019/1583 · also 1 corrigendum
 * @param corrigenda the corrigenda it integrates, by name, for made's hover; none if none
 * @param asked the date asked, when it isn't the version's first day: The version that applied on 1
 *     June 2021
 * @param lags where its text lags the law: on the date asked, or else over its period
 * @param action where the date form goes: the act in the page's language
 * @param on the date the form starts at: the date asked
 * @param first the earliest date the form takes: the first version's
 */
public record ReadingView(
    String celex,
    String eurLex,
    String period,
    String made,
    @Nullable String corrigenda,
    @Nullable String asked,
    List<String> lags,
    String action,
    String on,
    String first) {

  public ReadingView {
    lags = List.copyOf(lags);
  }

  /**
   * The card of {@code version}, the one that applies on {@code asked}, in {@code language}, on
   * {@code today}.
   */
  public static ReadingView of(
      Act act, Version version, LocalDate asked, Language language, LocalDate today) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var askedFor = !asked.equals(version.date());
    var corrigenda = version.integrates().stream().filter(Wording::corrigendum).toList();
    return new ReadingView(
        version.celex(),
        Links.eurLex(version.celex(), language),
        period(act, version, today),
        Wording.made(version),
        corrigenda.isEmpty() ? null : names(corrigenda),
        askedFor
            ? "The version that "
                + (asked.isBefore(today) ? "applied" : "applies")
                + " on "
                + Wording.date(asked)
            : null,
        askedFor
            ? act.lags().stream()
                .filter(lag -> !lag.from().isAfter(asked))
                .filter(lag -> asked.isBefore(lag.includedIn().date()))
                .map(Wording::lagOn)
                .toList()
            : act.notYetConsolidated(version).stream().map(Wording::lagOf).toList(),
        Links.act(eli, language),
        asked.toString(),
        act.timeline().versions().getFirst().date().toString());
  }

  /**
   * Applied 15 March 2021 to 30 December 2021; Applies from 22 March 2026 · current version · may
   * still be revised.
   */
  private static String period(Act act, Version version, LocalDate today) {
    var next = act.timeline().successor(version);
    var period =
        next.map(
                after -> {
                  var last = after.date().minusDays(1);
                  return (last.isBefore(today) ? "Applied " : "Applies ")
                      + Wording.date(version.date())
                      + " to "
                      + Wording.date(last);
                })
            .orElseGet(() -> "Applies from " + Wording.date(version.date()));
    if (act.versionOn(today).filter(version::equals).isPresent()) {
      period += " · current version";
    }
    return version.interim() ? period + " · may still be revised" : period;
  }

  private static String names(List<ActIdentifier> acts) {
    return acts.stream().map(Wording::act).collect(Collectors.joining(", "));
  }
}
