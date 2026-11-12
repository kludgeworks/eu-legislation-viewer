// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
 *   <li>a form that asks for the law on another day for that end, and keeps the other end
 * </ul>
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
 */
public record CompareView(Card from, Card to, @Nullable String acts, @Nullable String note) {

  /** Returns the same comparison, with {@code note} under the cards. */
  public CompareView noting(@Nullable String note) {
    return new CompareView(from, to, acts, note);
  }

  /**
   * Returns the cards of the comparison of the law on {@code fromDay}, which {@code from} gives,
   * with the law on {@code toDay}, which {@code to} gives, for {@code act}, on {@code today}. The
   * chips of the acts between them load from {@code acts}.
   */
  public static CompareView of(
      Act act,
      Version from,
      LocalDate fromDay,
      Version to,
      LocalDate toDay,
      Language language,
      LocalDate today,
      String acts) {
    var action = Links.compare(eli(act), language);
    return new CompareView(
        Card.of(act, from, fromDay, End.FROM, toDay, language, today, action),
        Card.of(act, to, toDay, End.TO, fromDay, language, today, action),
        acts,
        null);
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
      LocalDate today) {
    var action = Links.compare(eli(act), language);
    return new CompareView(
        Card.before(fromDay, End.FROM, toDay, action),
        to == null
            ? Card.before(toDay, End.TO, fromDay, action)
            : Card.of(act, to, toDay, End.TO, fromDay, language, today, action),
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
   * @param name the field of the date form for this end: {@code from} or {@code to}
   * @param on the date that the form starts at. This is the day.
   * @param keep the field of the other end, which the form sends unchanged
   * @param kept the day of the other end
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
      String name,
      String on,
      String keep,
      String kept,
      String action) {

    /**
     * Returns the card of the law on {@code day}, which {@code version} gives, as the {@code end}
     * of the comparison. {@code other} is the day at the other end.
     */
    static Card of(
        Act act,
        Version version,
        LocalDate day,
        End end,
        LocalDate other,
        Language language,
        LocalDate today,
        String action) {
      var published = Wording.makers(act, version, List.of());
      return CompareViewCardBuilder.builder()
          .kind(end.kind)
          .day(Dates.inWords(day))
          .version(Wording.which(act, version, day, today, language))
          .made(Wording.made(act, version, List.of(), language))
          .published(published.isEmpty() ? null : act.citations(published, language))
          .celex(version.celex())
          .eurLex(version.eurLex(language).toString())
          .name(end.field)
          .on(day.toString())
          .keep(end.other().field)
          .kept(other.toString())
          .action(action)
          .build();
    }

    /** Returns the card of {@code day}, a day before the act's first version. */
    static Card before(LocalDate day, End end, LocalDate other, String action) {
      return CompareViewCardBuilder.builder()
          .kind(end.kind)
          .day(Dates.inWords(day))
          .name(end.field)
          .on(day.toString())
          .keep(end.other().field)
          .kept(other.toString())
          .action(action)
          .build();
    }
  }
}
