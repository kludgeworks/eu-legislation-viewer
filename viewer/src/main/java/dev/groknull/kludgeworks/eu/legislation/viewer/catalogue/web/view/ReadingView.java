// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The Reading card of the page of an act: the law on the day that the reader asked for. The card
 * shows:
 *
 * <ul>
 *   <li>the day, as its headline: "The law on 14 February 2022"
 *   <li>which version that is and how long it applied: "From the version of 31 December 2021 ·
 *       applied until 28 February 2022", or "A new version starts on this day · …" on its first day
 *   <li>the acts that made the version
 *   <li>where the act was in force but did not apply yet, or where the text lags the law on the day
 *   <li>the version's consolidated CELEX, small, with a copy button
 *   <li>a form that asks for the law on another day
 *   <li>the link to the comparison with the version before
 * </ul>
 *
 * <p>Before the act's first version, there is no version: the card shows the day and a dash, and
 * the panel in place of the text says why.
 *
 * @param title the page's title. For example, "2015/1998 · the law on 14 Feb 2022".
 * @param day the day asked, in words. For example, "14 February 2022".
 * @param version which version applies on the day, and how long it applies. Null before the first
 *     version.
 * @param made what made the version. For example, "made by 2019/103, 2019/1583, 2020/910". Null
 *     before the first version.
 * @param published how the acts that made the version were published, one line for each act. The
 *     hover of {@code made} lists them the same way. Null if there are none.
 * @param notes what the reader must know about the day besides a lag: that the act was in force but
 *     did not apply yet
 * @param lags where the text of the version lags the law on the day: an act already applied that
 *     the version does not include yet
 * @param celex the consolidated CELEX of the version. Null before the first version.
 * @param eurLex the address of the version on EUR-Lex, in the language of the page. Null before the
 *     first version.
 * @param action the address the date form goes to: the act in the language of the page
 * @param on the date that the form starts at. This is the day asked.
 * @param first the earliest date that the form accepts. This is the date of the first version.
 * @param compare the address of the comparison of the version with the version before, in the
 *     language of the page. For the first version, the comparison is with the version after. Null
 *     where there is nothing to compare.
 * @param comparing the text of the compare link: "Compare with the version before" or "Compare with
 *     the version after"
 */
public record ReadingView(
    String title,
    String day,
    @Nullable String version,
    @Nullable String made,
    @Nullable String published,
    List<String> notes,
    List<String> lags,
    @Nullable String celex,
    @Nullable String eurLex,
    String action,
    String on,
    String first,
    @Nullable String compare,
    String comparing) {

  public ReadingView {
    notes = List.copyOf(notes);
    lags = List.copyOf(lags);
  }

  /**
   * Returns the Reading card of the law on {@code asked}, which {@code version} of {@code act}
   * gives, in {@code language}.
   *
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
    var makers = Wording.makers(act, version, named);
    var timeline = act.timeline().in(language).filter(each -> each.versions().contains(version));
    var before = timeline.flatMap(each -> each.predecessor(version));
    var after = timeline.flatMap(each -> each.successor(version));
    var starts =
        asked.equals(version.date())
            ? "A new version starts on this day"
            : "From the version of " + Dates.inWords(version.date());
    var until = Wording.until(act, version, today, language);
    return new ReadingView(
        title(act, asked),
        Dates.inWords(asked),
        until.isEmpty() ? starts : starts + " · " + until,
        Wording.made(act, version, named, language),
        makers.isEmpty() ? null : act.citations(makers, language),
        Wording.notApplying(act, version, asked).stream().toList(),
        act.lags().stream()
            .filter(lag -> !lag.from().isAfter(asked))
            .filter(lag -> asked.isBefore(lag.includedIn().date()))
            .map(lag -> Wording.lagOn(lag, asked))
            .toList(),
        version.celex(),
        version.eurLex(language).toString(),
        Links.act(eli, language),
        asked.toString(),
        first(act).toString(),
        before
            .map(each -> Links.compare(eli, each, version, language))
            .or(() -> after.map(each -> Links.compare(eli, version, each, language)))
            .orElse(null),
        before.isPresent() ? "Compare with the version before" : "Compare with the version after");
  }

  /**
   * Returns the Reading card of the law on {@code asked}, a day before the first version of {@code
   * act}. There is no version: the card shows the day and a dash.
   */
  public static ReadingView before(Act act, LocalDate asked, Language language) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    return new ReadingView(
        title(act, asked),
        Dates.inWords(asked),
        null,
        null,
        null,
        List.of(),
        List.of(),
        null,
        null,
        Links.act(eli, language),
        asked.toString(),
        first(act).toString(),
        null,
        "Compare with the version after");
  }

  /** Returns the page's title. For example, "2015/1998 · the law on 14 Feb 2022". */
  private static String title(Act act, LocalDate asked) {
    return act.id().cited() + " · the law on " + Dates.inShort(asked);
  }

  private static LocalDate first(Act act) {
    return act.timeline().versions().getFirst().date();
  }
}
