// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The From and To cards of a comparison (Design: Mode: Compare), and the acts between them: each
 * card the version by its CELEX, its period, the acts that made it, where its text lags the law,
 * and a form asking for the version on another date for that end, the other kept; the acts, those
 * the versions after From up to To integrate, by number.
 *
 * @param from the From card
 * @param to the To card
 * @param actsHead how many acts are between them: 1 act between them
 * @param acts the acts between them, by number, in the versions' order; their corrigenda left out
 */
public record CompareView(Card from, Card to, String actsHead, List<String> acts) {

  public CompareView {
    acts = List.copyOf(acts);
  }

  /** The cards of {@code act}'s comparison of {@code from} with {@code to}, on {@code today}. */
  public static CompareView of(
      Act act, Version from, Version to, Language language, LocalDate today) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var action = Links.compare(eli, language);
    var first = act.timeline().versions().getFirst().date().toString();
    var acts =
        act.timeline().versions().stream()
            .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
            .flatMap(v -> v.integrates().stream())
            .filter(id -> !Wording.corrigendum(id))
            .distinct()
            .map(Wording::act)
            .toList();
    return new CompareView(
        Card.of(act, from, End.FROM, to, language, today, action, first),
        Card.of(act, to, End.TO, from, language, today, action, first),
        acts.size() + (acts.size() == 1 ? " act between them" : " acts between them"),
        acts);
  }

  /** An end of a comparison: what its card is headed, and its field in the comparison's address. */
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
   * A version's card.
   *
   * @param kind which end: From, To
   * @param celex the version's CELEX
   * @param eurLex the version on EUR-Lex, in the page's language
   * @param period when it applied or applies
   * @param made what made it
   * @param corrigenda the corrigenda it integrates, by name, for made's hover; none if none
   * @param lags where its text lags the law over its period
   * @param name the date form's field for this end: from, to
   * @param on the date the form starts at: the version's first day
   * @param keep the other end's field, which the form sends unchanged
   * @param kept the other end's date
   * @param action where the form goes: the act's comparisons in the page's language
   * @param first the earliest date the form takes: the first version's
   */
  @RecordBuilder
  public record Card(
      String kind,
      String celex,
      String eurLex,
      String period,
      String made,
      @Nullable String corrigenda,
      List<String> lags,
      String name,
      String on,
      String keep,
      String kept,
      String action,
      String first) {

    public Card {
      lags = List.copyOf(lags);
    }

    /** {@code version}'s card as the comparison's {@code end}, {@code other} the other end. */
    static Card of(
        Act act,
        Version version,
        End end,
        Version other,
        Language language,
        LocalDate today,
        String action,
        String first) {
      List<ActIdentifier> corrigenda =
          version.integrates().stream().filter(Wording::corrigendum).toList();
      return CompareViewCardBuilder.builder()
          .kind(end.kind)
          .celex(version.celex())
          .eurLex(Links.eurLex(version.celex(), language))
          .period(ReadingView.period(act, version, today))
          .made(Wording.made(version))
          .corrigenda(corrigenda.isEmpty() ? null : ReadingView.names(corrigenda))
          .lags(act.notYetConsolidated(version).stream().map(Wording::lagOf).toList())
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
