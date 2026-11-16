// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextMarks;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Unshown;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The From and To cards of a comparison, and the slot of the acts between them. From is the law on
 * one day, To the law on another.
 *
 * <p>Each card shows:
 *
 * <ul>
 *   <li>its end and the day, as its headline: "From · the law on", "22 March 2026"
 *   <li>which version gives the law on the day, and how long it applied: "A new version starts on
 *       this day · applied until 30 September 2026"
 *   <li>the acts that made the version
 *   <li>the version's consolidated CELEX, small, with a copy button
 *   <li>where the text of the version is not the law on the day, why, as the Reading card says it:
 *       "In force. Does not apply yet.", "Applies from 1 February 2016 · Article 2 of 2015/1998."
 *   <li>a form that asks for the law on another day for that end, and keeps the other end
 * </ul>
 *
 * <p>Where the comparison shows the changes of one act that applied before the To version included
 * it, a notice under the cards says that the law on those days was the From text with these
 * changes, {@link Act#unshown}: "From 1 to 6 June 2017 the law was the “From” text with the changes
 * of 2017/815." The Reading card of a late text links here, with the day it shows. If that day is
 * one of those days, the notice links back to it: "Back to 3 June 2017".
 *
 * <p>A day before the act's first version has no version: its card shows the day and a dash.
 *
 * <p>The acts between the cards are the comparison module's: they come with how many differences
 * each act made, and only that module knows. Their chips load into a slot between the cards, from
 * the comparison module's address, {@code acts}.
 *
 * @param from the From card
 * @param to the To card
 * @param acts the address that the chips of the acts between the two versions load from. Null if
 *     there is nothing to compare.
 * @param note what the reader should know about how the page chose the acts that it shows. For
 *     example, the reader asked for an act that is not between the two versions. Null if there is
 *     nothing to say.
 * @param unshown the notice of the days on which the law was the From text with the changes shown,
 *     which no version shows. Null if there are none.
 * @param back the address of the Read page of the day that the reader came from, one of those days.
 *     Null if the reader did not come from one.
 * @param backTo the words of the link to {@code back}: "Back to 3 June 2017". Null without it.
 */
public record CompareView(
    Card from,
    Card to,
    @Nullable String acts,
    @Nullable String note,
    @Nullable String unshown,
    @Nullable String back,
    @Nullable String backTo) {

  /** Returns the same comparison, with {@code note} under the cards. */
  public CompareView noting(@Nullable String note) {
    return new CompareView(from, to, acts, note, unshown, back, backTo);
  }

  /**
   * Returns the cards of the comparison of the law on {@code fromDay}, which {@code from} gives,
   * with the law on {@code toDay}, which {@code to} gives, for {@code act}, on {@code today}. The
   * chips of the acts between them load from {@code acts}.
   *
   * @param marks the marks of the built texts in {@code language}, by version, which tell where a
   *     text is not the law
   * @param only the act whose changes alone the comparison shows. Null if it shows each act's.
   * @param on the day of the Read page that the reader came from. Null if not known.
   */
  public static CompareView of(
      Act act,
      Version from,
      LocalDate fromDay,
      Version to,
      LocalDate toDay,
      Language language,
      LocalDate today,
      String acts,
      Map<Version, TextMarks> marks,
      @Nullable ActIdentifier only,
      @Nullable LocalDate on) {
    var action = Links.compare(eli(act), language);
    var unshown =
        only == null ? Optional.<Unshown>empty() : act.unshown(from, to, only, language, marks);
    var back = unshown.isPresent() && on != null && unshown.get().on(on) ? on : null;
    return new CompareView(
        Card.of(act, from, fromDay, End.FROM, toDay, language, today, action, marks),
        Card.of(act, to, toDay, End.TO, fromDay, language, today, action, marks),
        acts,
        null,
        unshown.map(each -> unshown(act, each)).orElse(null),
        back == null ? null : Links.read(eli(act), back, language),
        back == null ? null : "Back to " + Dates.inWords(back));
  }

  /** Returns the notice of the days on which the law was the From text with the changes shown. */
  private static String unshown(Act act, Unshown unshown) {
    var days =
        unshown.first().equals(unshown.last())
            ? "On " + Dates.inWords(unshown.first())
            : "From " + Dates.range(unshown.first(), unshown.last());
    return days
        + " the law was the “From” text with the changes of "
        + act.cited(unshown.act())
        + ".";
  }

  /**
   * Returns the cards of a comparison whose From, {@code fromDay}, is a day before the first
   * version of {@code act}. The From card shows the day and a dash. The To card shows the law on
   * {@code toDay}, which {@code to} gives, or a dash too if there is no version then.
   */
  public static CompareView before(
      Act act,
      LocalDate fromDay,
      @Nullable Version to,
      LocalDate toDay,
      Language language,
      LocalDate today,
      Map<Version, TextMarks> marks) {
    var action = Links.compare(eli(act), language);
    var starts = TimelineView.starts(act, language);
    return new CompareView(
        Card.before(fromDay, End.FROM, toDay, starts, action),
        to == null
            ? Card.before(toDay, End.TO, fromDay, starts, action)
            : Card.of(act, to, toDay, End.TO, fromDay, language, today, action, marks),
        null,
        null,
        null,
        null,
        null);
  }

  private static Eli eli(Act act) {
    return Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
  }

  /**
   * An end of a comparison. It has the heading of its card and its field in the address of the
   * comparison.
   */
  private enum End {
    FROM("From · the law on", "from"),
    TO("To · the law on", "to");

    private final String kind;
    private final String field;

    End(String kind, String field) {
      this.kind = kind;
      this.field = field;
    }

    End other() {
      return this == FROM ? TO : FROM;
    }
  }

  /**
   * The card of an end of the comparison: the law on a day.
   *
   * @param kind which end the card is: "From · the law on" or "To · the law on"
   * @param day the day, in words. For example, "22 March 2026".
   * @param version which version gives the law on the day, and how long it applies. Null before the
   *     first version.
   * @param made what made the version. Null before the first version.
   * @param published how the acts that made the version were published, one line for each act. The
   *     hover of {@code made} lists them the same way. Null if there are none.
   * @param celex the consolidated CELEX of the version. Null before the first version.
   * @param eurLex the address of the version on EUR-Lex, in the language of the page. Null before
   *     the first version.
   * @param divergences why the text of the version is not the law on the day, as the Reading card
   *     says it. Their links are not shown. Empty if it is the law.
   * @param name the field of the date form for this end: {@code from} or {@code to}
   * @param on the date that the form starts at. This is the day.
   * @param keep the field of the other end, which the form sends unchanged
   * @param kept the day of the other end
   * @param starts the days that a new version starts, which the date picker rings: ISO dates in
   *     order, separated by spaces
   * @param action the address the form goes to: the comparisons of the act in the language of the
   *     page
   */
  @RecordBuilder
  public record Card(
      String kind,
      String day,
      @Nullable String version,
      @Nullable String made,
      @Nullable String published,
      @Nullable String celex,
      @Nullable String eurLex,
      List<DivergenceView> divergences,
      String name,
      String on,
      String keep,
      String kept,
      String starts,
      String action) {

    /**
     * Returns the card of the law on {@code day}, which {@code version} gives, as the {@code end}
     * of the comparison. {@code other} is the day at the other end. {@code marks} are the marks of
     * the built texts, which tell where the text is not the law on the day.
     */
    static Card of(
        Act act,
        Version version,
        LocalDate day,
        End end,
        LocalDate other,
        Language language,
        LocalDate today,
        String action,
        Map<Version, TextMarks> marks) {
      var published = Wording.makers(act, version, List.of());
      return CompareViewCardBuilder.builder()
          .kind(end.kind)
          .day(Dates.inWords(day))
          .version(Wording.which(act, version, day, today, language))
          .made(Wording.made(act, version, List.of(), language))
          .published(published.isEmpty() ? null : act.citations(published, language))
          .celex(version.celex())
          .eurLex(version.eurLex(language).toString())
          .divergences(
              act.divergences(day, language, marks).stream()
                  .map(each -> DivergenceView.of(act, each, version, day, language, today))
                  .toList())
          .name(end.field)
          .on(day.toString())
          .keep(end.other().field)
          .kept(other.toString())
          .starts(TimelineView.starts(act, language))
          .action(action)
          .build();
    }

    /** Returns the card of {@code day}, a day before the act's first version. */
    static Card before(LocalDate day, End end, LocalDate other, String starts, String action) {
      return CompareViewCardBuilder.builder()
          .kind(end.kind)
          .day(Dates.inWords(day))
          .divergences(List.of())
          .name(end.field)
          .on(day.toString())
          .keep(end.other().field)
          .kept(other.toString())
          .starts(starts)
          .action(action)
          .build();
    }
  }
}
