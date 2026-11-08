// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The Reading card of the page of an act. The card shows:
 *
 * <ul>
 *   <li>the version that the reader reads, by its CELEX
 *   <li>its period
 *   <li>the acts that made it
 *   <li>the date that the reader asked for, when it is not the first day of the version
 *   <li>where the text lags the law
 *   <li>a form that asks for the version on another date
 *   <li>the link to its comparison with the version before
 * </ul>
 *
 * @param celex the CELEX of the version
 * @param eurLex the address of the version on EUR-Lex, in the language of the page
 * @param period when the version applied or applies. For example, "Applied 15 March 2021 to 30
 *     December 2021".
 * @param made what made the version. For example, "made by 2019/103, 2019/1583 · also 1
 *     corrigendum".
 * @param published how the acts that made the version were published, one line for each act. The
 *     hover of {@code made} lists them the same way. Null if there are none.
 * @param asked the date that the reader asked for, when it is not the first day of the version. For
 *     example, "The version that applied on 1 June 2021".
 * @param lags where the text of the version lags the law. This is on the date asked, if the reader
 *     asked for a date. Else it is during the period of the version.
 * @param mayLag where the text of the version may lag the law, because the date of application of
 *     an act is not known. These show in grey.
 * @param action the address the date form goes to: the act in the language of the page
 * @param on the date that the form starts at. This is the date asked.
 * @param first the earliest date that the form accepts. This is the date of the first version.
 * @param compare the address of the comparison of the version with the version before, in the
 *     language of the page. For the first version, the comparison is with the version after. Null
 *     where the version is the only one.
 * @param comparing the text of the compare link: "Compare with the version before" or "Compare with
 *     the version after"
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
   * Returns the card of {@code version}, which is the version that applies on {@code asked}.
   *
   * @param language the language of the page
   * @param today the date today
   * @param named the acts that the text of the version names and the text of the version before
   *     does not. They are used for a version for which the Publications Office lists no acts.
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
    var applies = Wording.applies(act, version);
    return new ReadingView(
        version.celex(),
        version.eurLex(language).toString(),
        Wording.period(act, version, today),
        Wording.made(act, version, named),
        makers.isEmpty() ? null : act.citations(makers, language),
        askedFor
            ? "The version that "
                + (asked.isBefore(today) ? "applied" : "applies")
                + " on "
                + Dates.inWords(asked)
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
}
