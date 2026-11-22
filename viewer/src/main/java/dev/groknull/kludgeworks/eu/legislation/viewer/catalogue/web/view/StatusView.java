// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.DeferredApplication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Standing;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The status lines of the Read page: where the text that the reader gets is not the law on the day.
 * One line is for one act. The order in each line is: the act, its state on the day, the date, and
 * how many provisions. For example, for 2018/1862 on 1 August 2022:
 *
 * <ul>
 *   <li>"2018/1862 applied in part. It applies in full from 7 March 2023 · Article 79(5) of
 *       2018/1862."
 *   <li>"2022/1190 was in force and did not apply yet. It applies from 15 October 2026 · Article 2
 *       of 2022/1190. 17 provisions, with a divergence note in the text."
 *   <li>"2021/1133 was in force. Article 6 of 2021/1133 sets the day from which it applies. The
 *       data does not give a date for that day. 3 provisions, not in this text."
 * </ul>
 *
 * <p>The acts that get a line are the act itself while its application is deferred ({@link
 * Act#deferredApplicationOn}), first, and the acts of {@link Act#divergences}: a prospective
 * amendment (an amending act whose text is in the text before it applies), and a pending amendment
 * (an amending act with changes that the text does not show). The tense obeys the day.
 *
 * @param day the day, in words
 * @param lines the first lines, three at most
 * @param more the other lines, which show when the reader asks for all
 */
public record StatusView(String day, List<Line> lines, List<Line> more) {

  /** How many lines show before the reader asks for all. */
  private static final int SHOWN = 3;

  public StatusView {
    lines = List.copyOf(lines);
    more = List.copyOf(more);
  }

  /** Returns the view with no lines, for a day before the first version of an act. */
  public static StatusView none(LocalDate asked) {
    return new StatusView(Dates.inWords(asked), List.of(), List.of());
  }

  /**
   * Returns the status lines of {@code act} on {@code asked}, a day on which a version gives the
   * text, in {@code language}.
   *
   * @param today the date today, which gives the tense
   * @param tags the tags of the built texts in {@code language}, by version
   */
  public static StatusView of(
      Act act, LocalDate asked, Language language, LocalDate today, Map<Version, TextTags> tags) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var all = new ArrayList<Line>();
    act.deferredApplicationOn(asked, language)
        .ifPresent(deferred -> all.add(deferred(act, deferred, asked, today)));
    act.divergences(asked, language, tags).stream()
        .map(each -> line(act, each, eli, asked, language, today))
        .forEach(all::add);
    var shown = Math.min(all.size(), SHOWN);
    return new StatusView(
        Dates.inWords(asked), all.subList(0, shown), all.subList(shown, all.size()));
  }

  /** Returns how many lines there are. */
  public int size() {
    return lines.size() + more.size();
  }

  /**
   * Returns the line of the act itself while its application is deferred. For example, "2018/1862
   * applied in part. It applies in full from 7 March 2023 · Article 79(5) of 2018/1862."
   */
  private static Line deferred(
      Act act, DeferredApplication deferred, LocalDate asked, LocalDate today) {
    var past = asked.isBefore(today);
    var state =
        States.phrase(
            deferred.partial() ? Standing.Whole.IN_PART : Standing.Whole.NOT_APPLYING, past);
    return new Line(
        act.cited(act.id()),
        null,
        state
            + ". It "
            + applies(deferred.day(), today)
            + (deferred.partial() ? " in full from " : " from ")
            + Dates.inWords(deferred.day())
            + clause(act, deferred.inFull(), act.id())
            + ".");
  }

  private static Line line(
      Act act,
      Divergence divergence,
      Eli eli,
      LocalDate asked,
      Language language,
      LocalDate today) {
    var past = asked.isBefore(today);
    var cited = act.cited(divergence.act());
    var from = divergence.appliesFrom();
    var day = Dates.inWords(from);
    var href = Links.amending(eli, divergence.act(), asked.equals(today) ? null : asked, language);
    var count =
        divergence.places().stream()
            .map(place -> place.location().place().words())
            .distinct()
            .count();
    return switch (divergence.kind()) {
      case PROSPECTIVE ->
          new Line(
              cited,
              href,
              States.phrase(Standing.Whole.NOT_APPLYING, past)
                  + ". It "
                  + applies(from, today)
                  + " from "
                  + day
                  + clause(act, divergence.applies(), divergence.act())
                  + "."
                  + count(count, ", with a divergence note in the text."));
      case PENDING ->
          new Line(
              cited,
              href,
              applies(from, today)
                  + " from "
                  + day
                  + clause(act, divergence.applies(), divergence.act())
                  + "."
                  + shown(divergence)
                  + count(count, ", not in this text."));
      case UNDATED ->
          new Line(
              cited,
              href,
              States.phrase(Standing.Whole.NOT_KNOWN, past)
                  + ". "
                  + setBy(act, divergence)
                  + count(count, ", not in this text."));
    };
  }

  /** Returns "applies" for a day after today, and "applied" for today or a day before. */
  private static String applies(LocalDate from, LocalDate today) {
    return from.isAfter(today) ? "applies" : "applied";
  }

  /**
   * Returns the clause that sets {@code date} of the act {@code of}, with a separator before it.
   * For example, " · Article 99(2) of 2016/679". Returns an empty text if Cellar names none.
   */
  private static String clause(Act act, ActDate date, ActIdentifier of) {
    return date.clause().provisions().stream()
        .findFirst()
        .map(provision -> " · " + provision.cited() + " of " + act.cited(of))
        .orElse("");
  }

  /** Returns from when the text shows a pending amendment, as a sentence with a space before it. */
  private static String shown(Divergence pending) {
    var included = pending.included();
    return included == null
        ? " No text shows it yet."
        : " The text shows it from " + Dates.inWords(included.date()) + ".";
  }

  /**
   * Returns what sets the day of an undated pending amendment, whose day the data does not give.
   */
  private static String setBy(Act act, Divergence undated) {
    var clause =
        act.applying(undated.act(), Set.of())
            .map(applying -> Wording.clause(applying.dates(), undated.act()))
            .orElse("");
    return clause.isEmpty()
        ? "The data does not give the day from which it applies."
        : clause
            + " sets the day from which it applies. The data does not give a date for that day.";
  }

  /**
   * Returns how many provisions, with {@code where} after it and a space before it. For example, "
   * 17 provisions, with a divergence note in the text.". Returns an empty text if Cellar names no
   * provision.
   */
  private static String count(long count, String where) {
    return count == 0 ? "" : " " + (count == 1 ? "1 provision" : count + " provisions") + where;
  }

  /**
   * One line, about one act.
   *
   * @param act the act, as cited. For example, "2022/1190".
   * @param href the address of the page of the act, for an amending act. Null for the act itself.
   * @param words the rest of the line: the state, the date and the count
   */
  public record Line(String act, @Nullable String href, String words) {}
}
