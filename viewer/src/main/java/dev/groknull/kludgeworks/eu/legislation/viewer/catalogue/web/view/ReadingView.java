// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The Reading card of an act's page (Design: Mode: Read): the version being read, by its CELEX, its
 * period, the acts that made it; the date asked, when it isn't the version's first day; where the
 * text lags the law (Design: Dates); a form asking for the version on another date; and its
 * comparison with the version before.
 *
 * @param celex the version's CELEX
 * @param eurLex the version on EUR-Lex, in the page's language
 * @param period when it applied or applies: Applied 15 March 2021 to 30 December 2021
 * @param made what made it: made by 2019/103, 2019/1583 · also 1 corrigendum
 * @param published how the acts that made it were published, a line each, as made's hover lists
 *     them (Design D-21); none if none
 * @param asked the date asked, when it isn't the version's first day: The version that applied on 1
 *     June 2021
 * @param lags where its text lags the law: on the date asked, or else over its period
 * @param mayLag where its text may lag the law, an act's date of application not known, in grey
 *     (Design D-24)
 * @param action where the date form goes: the act in the page's language
 * @param on the date the form starts at: the date asked
 * @param first the earliest date the form takes: the first version's
 * @param compare its comparison with the version before in the page's language; for the first, with
 *     the version after (Design D-22); none where it is the only one
 * @param comparing what compare says: Compare with the version before; … after
 */
public record ReadingView(
    String celex,
    String eurLex,
    String period,
    String made,
    @Nullable String published,
    @Nullable String asked,
    List<String> lags,
    List<String> mayLag,
    String action,
    String on,
    String first,
    @Nullable String compare,
    String comparing) {

  public ReadingView {
    lags = List.copyOf(lags);
    mayLag = List.copyOf(mayLag);
  }

  /**
   * The card of {@code version}, the one that applies on {@code asked}, in {@code language}, on
   * {@code today}; {@code named}, the acts its text names that the version before's doesn't, for a
   * version the Publications Office lists none for (Design D-19).
   */
  public static ReadingView of(
      Act act,
      Version version,
      LocalDate asked,
      Language language,
      LocalDate today,
      List<ActIdentifier> named) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var askedFor = !asked.equals(version.date());
    var makers = Wording.makers(act, version, named);
    var timeline = act.timeline().in(language).filter(each -> each.versions().contains(version));
    var before = timeline.flatMap(each -> each.predecessor(version));
    var after = timeline.flatMap(each -> each.successor(version));
    var applies = applies(act, version);
    return new ReadingView(
        version.celex(),
        Links.eurLex(version.celex(), language),
        period(act, version, today),
        Wording.made(act, version, named),
        makers.isEmpty() ? null : Wording.published(act, makers, language),
        askedFor
            ? "The version that "
                + (asked.isBefore(today) ? "applied" : "applies")
                + " on "
                + Wording.date(asked)
                + (applies.isPresent() && asked.isBefore(applies.get())
                    ? " · the act was in force but didn’t apply yet"
                    : "")
            : null,
        askedFor
            ? act.lags().stream()
                .filter(lag -> !lag.from().isAfter(asked))
                .filter(lag -> asked.isBefore(lag.includedIn().date()))
                .map(Wording::lagOn)
                .toList()
            : act.notYetConsolidated(version).stream().map(Wording::lagOf).toList(),
        act.mayLag(version).stream().map(Wording::mayLag).toList(),
        Links.act(eli, language),
        asked.toString(),
        act.timeline().versions().getFirst().date().toString(),
        before
            .map(each -> Links.compare(eli, each, version, language))
            .or(() -> after.map(each -> Links.compare(eli, version, each, language)))
            .orElse(null),
        before.isPresent() ? "Compare with the version before" : "Compare with the version after");
  }

  /**
   * The day the act as published, {@code version}, first applied, after it entered into force
   * (Design D-17): its earliest date of application or taking effect after; none for a later
   * version, or an act that applied on entering into force.
   */
  static Optional<LocalDate> applies(Act act, Version version) {
    if (version.kind() != Version.Kind.ORIGINAL) {
      return Optional.empty();
    }
    return act.dates().stream()
        .filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE)
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .filter(day -> day.isAfter(version.date()))
        .min(Comparator.naturalOrder());
  }

  /**
   * Applied 15 March 2021 to 30 December 2021; Applies from 22 March 2026 · current version · may
   * still be revised; for the act as published, from its entry into force: In force from 15
   * November 2015 · applied 1 February 2016 to 28 February 2016.
   */
  static String period(Act act, Version version, LocalDate today) {
    var next = act.timeline().successor(version);
    var applies = applies(act, version);
    if (applies.isPresent()) {
      // the act as published: in force before it applied (Design D-17)
      var from = applies.get();
      var partial =
          act.dates().stream().filter(date -> from.equals(date.date())).allMatch(ActDate::partial);
      var inPart = partial ? " in part" : "";
      var period =
          "In force from "
              + Wording.date(version.date())
              + " · "
              + next.map(
                      after -> {
                        var last = after.date().minusDays(1);
                        return (last.isBefore(today) ? "applied" : "applies")
                            + inPart
                            + " "
                            + Wording.date(from)
                            + " to "
                            + Wording.date(last);
                      })
                  .orElseGet(() -> "applies" + inPart + " from " + Wording.date(from));
      return label(act, version, today, period);
    }
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
    return label(act, version, today, period);
  }

  /**
   * The period, labelled (Design D-18): current version, the one that applies today; next version,
   * the one after it, dated after today; may still be revised, an interim one.
   */
  private static String label(Act act, Version version, LocalDate today, String period) {
    var current = act.versionOn(today);
    var labelled = period;
    if (current.filter(version::equals).isPresent()) {
      labelled += " · current version";
    } else if (version.date().isAfter(today)
        && current.flatMap(act.timeline()::successor).filter(version::equals).isPresent()) {
      labelled += " · next version";
    }
    return version.interim() ? labelled + " · may still be revised" : labelled;
  }
}
