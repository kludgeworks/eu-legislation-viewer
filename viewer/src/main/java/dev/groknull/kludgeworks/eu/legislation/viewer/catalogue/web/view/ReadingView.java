// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LawOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
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
 *   <li>which version that is and how long it applied: "From the text of 31 December 2021 · applied
 *       until 28 February 2022", or "A new text starts on this day · …" on its first day
 *   <li>the acts that made the version
 *   <li>the status lines, where the text is not the law on the day: one line for each act
 *   <li>the version's consolidated CELEX, small, with a copy button
 *   <li>a form with a date, which asks for the law on another day
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
 * @param status the status lines: where the text of the version is not the law on the day, one line
 *     for each act
 * @param celex the consolidated CELEX of the version. Null before the first version.
 * @param eurLex the address of the version on EUR-Lex, in the language of the page. Null before the
 *     first version.
 * @param picker the day control. With two days, it goes to the comparison of the act.
 */
public record ReadingView(
    String title,
    String day,
    @Nullable String version,
    @Nullable String made,
    @Nullable String published,
    StatusView status,
    @Nullable String celex,
    @Nullable String eurLex,
    DayView picker) {

  /**
   * Returns the Reading card of the law on {@code asked}, which {@code version} of {@code act}
   * gives, in {@code language}.
   *
   * @param today the date today
   * @param named the acts that the text of the version names and the text of the version before
   *     does not. They are used for a version for which the Publications Office lists no acts.
   * @param law the text and the law on {@code asked}. It tells where the text is not the law.
   */
  public static ReadingView of(
      ActOverTime overTime,
      Version version,
      LocalDate asked,
      LocalDate today,
      List<ActIdentifier> named,
      LawOnADay law) {
    var act = overTime.act();
    var language = overTime.language();
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var makers = VersionWording.makers(act, version, named, law.madeBy());
    return new ReadingView(
        title(act, asked),
        Dates.inWords(asked),
        VersionWording.which(act, version, asked, today, language),
        VersionWording.made(act, version, named, law.madeBy(), language),
        makers.isEmpty() ? null : act.citations(makers, language),
        StatusView.of(overTime, law, today),
        version.celex(),
        version.eurLex(language).toString(),
        DayView.of(
            overTime, asked, null, Links.act(eli, language), Links.compare(eli, language), null));
  }

  /**
   * Returns the Reading card of the law on {@code asked}, a day before the first version of {@code
   * act}. There is no version: the card shows the day and a dash.
   */
  public static ReadingView before(ActOverTime overTime, LocalDate asked) {
    var act = overTime.act();
    var language = overTime.language();
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    return new ReadingView(
        title(act, asked),
        Dates.inWords(asked),
        null,
        null,
        null,
        StatusView.none(asked),
        null,
        null,
        DayView.of(
            overTime, asked, null, Links.act(eli, language), Links.compare(eli, language), null));
  }

  /** Returns the page's title. For example, "2015/1998 · the law on 14 Feb 2022". */
  public static String title(Act act, LocalDate asked) {
    return act.id().cited() + " · the law on " + Dates.inShort(asked);
  }
}
