// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The From and To cards of a comparison, and the slot of the acts between them.
 *
 * <p>Each card shows:
 *
 * <ul>
 *   <li>the version, by its CELEX
 *   <li>its period
 *   <li>the acts that made it
 *   <li>where its text lags the law
 *   <li>a form that asks for the version on another date for that end, and keeps the other end
 * </ul>
 *
 * <p>The acts between the cards are the comparison module's: they come with how many differences
 * each act made, and only that module knows. Their chips load into a slot between the cards, from
 * the comparison module's address, {@code acts}.
 *
 * @param from the From card
 * @param to the To card
 * @param acts the address that the chips of the acts between the two versions load from
 * @param note what the reader should know about how the page chose the versions or acts that it
 *     shows. For example, the page was opened without dates, or the reader asked for an act that is
 *     not between the two versions. Null if there is nothing to say.
 */
public record CompareView(Card from, Card to, String acts, @Nullable String note) {

  /** Returns the same comparison, with {@code note} under the cards. */
  public CompareView noting(@Nullable String note) {
    return new CompareView(from, to, acts, note);
  }

  /**
   * Returns the cards of the comparison of {@code from} with {@code to} for {@code act}, on {@code
   * today}. The chips of the acts between them load from {@code acts}.
   */
  public static CompareView of(
      Act act, Version from, Version to, Language language, LocalDate today, String acts) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var action = Links.compare(eli, language);
    var first = act.timeline().versions().getFirst().date().toString();
    return new CompareView(
        Card.of(act, from, End.FROM, to, language, today, action, first),
        Card.of(act, to, End.TO, from, language, today, action, first),
        acts,
        null);
  }

  /**
   * An end of a comparison. It has the heading of its card and its field in the address of the
   * comparison.
   */
  private enum End {
    FROM("From", "from"),
    TO("To", "to");

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
   * The card of a version.
   *
   * @param kind which end the card is: From or To
   * @param celex the CELEX of the version
   * @param eurLex the address of the version on EUR-Lex, in the language of the page
   * @param period when the version applied or applies
   * @param made what made the version
   * @param published how the acts that made the version were published, one line for each act. The
   *     hover of {@code made} lists them the same way. Null if there are none.
   * @param lags where the text of the version lags the law during its period
   * @param mayLag where the text of the version may lag the law, because the date of application of
   *     an act is not known
   * @param name the field of the date form for this end: {@code from} or {@code to}
   * @param on the date that the form starts at. This is the first day of the version.
   * @param keep the field of the other end, which the form sends unchanged
   * @param kept the date of the other end
   * @param action the address the form goes to: the comparisons of the act in the language of the
   *     page
   * @param first the earliest date that the form accepts. This is the date of the first version.
   */
  @RecordBuilder
  public record Card(
      String kind,
      String celex,
      String eurLex,
      String period,
      String made,
      @Nullable String published,
      List<String> lags,
      List<String> mayLag,
      String name,
      String on,
      String keep,
      String kept,
      String action,
      String first) {

    public Card {
      lags = List.copyOf(lags);
      mayLag = List.copyOf(mayLag);
    }

    /**
     * Returns the card of {@code version} as the {@code end} of the comparison. {@code other} is
     * the version at the other end.
     */
    static Card of(
        Act act,
        Version version,
        End end,
        Version other,
        Language language,
        LocalDate today,
        String action,
        String first) {
      var published = Wording.makers(act, version, List.of());
      return CompareViewCardBuilder.builder()
          .kind(end.kind)
          .celex(version.celex())
          .eurLex(version.eurLex(language).toString())
          .period(Wording.period(act, version, today))
          .made(Wording.made(act, version, List.of()))
          .published(published.isEmpty() ? null : act.citations(published, language))
          .lags(act.notYetConsolidated(version).stream().map(Wording::lagOf).toList())
          .mayLag(act.mayLag(version).stream().map(Wording::mayLag).toList())
          .name(end.field)
          .on(version.date().toString())
          .keep(end.other().field)
          .kept(other.date().toString())
          .action(action)
          .first(first)
          .build();
    }
  }
}
