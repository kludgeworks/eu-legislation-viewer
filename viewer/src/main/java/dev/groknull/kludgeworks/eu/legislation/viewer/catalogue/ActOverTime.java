// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.LegalState;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.Stands;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What the rules tell of an act, for each day: the states of the act itself, its amending acts with
 * how each stands, and its corrigenda. It does not depend on the day that a reader asks for, so it
 * is worked out one time for an act and a language ({@link #of}). The history on a day is read from
 * it ({@link #on}).
 *
 * @param act the act
 * @param language the language of the text whose changes count
 * @param state the states of the act itself, with the days of each
 * @param amendingActs each amending act of the act, newest first
 * @param corrigenda the corrigenda of the act that correct the text in the language
 */
public record ActOverTime(
    Act act,
    Language language,
    Periods<LegalState> state,
    List<Amending> amendingActs,
    List<History.Corrigendum> corrigenda) {

  public ActOverTime {
    amendingActs = List.copyOf(amendingActs);
    corrigenda = List.copyOf(corrigenda);
  }

  /**
   * An amending act, for each day.
   *
   * @param act the amending act
   * @param stands how it stands, with the days on which it stands so
   * @param applying when it applies, as its dates tell. Null if Cellar has no dates of it.
   * @param changes its changes to the text in the language
   */
  public record Amending(
      ActIdentifier act,
      Periods<Stands> stands,
      @Nullable Applying applying,
      List<Amendment> changes) {
    public Amending {
      changes = List.copyOf(changes);
    }

    /** Returns the amending act as it stands on {@code day}. */
    public History.AmendingAct on(LocalDate day) {
      return new History.AmendingAct(act, stands.on(day), applying, changes);
    }
  }

  /**
   * Works out what the rules tell of {@code act}, by its changes to the text in {@code language}.
   * It applies the rules in order, each one time for each act and each change:
   *
   * <ol>
   *   <li>What the dates of the act, and of each amending act, tell ({@link DatedAct}).
   *   <li>The states of the act itself.
   *   <li>The amending acts, in order, each with its changes.
   *   <li>The states of each change, and from them how its amending act stands.
   *   <li>The corrigenda.
   * </ol>
   */
  public static ActOverTime of(Act act, Language language) {
    var state = DatedAct.of(act, act.id()).state(act);
    var amendingActs =
        amendingActsOf(act, language).stream().map(each -> amending(act, each, language)).toList();
    var corrigenda =
        TheHistoryOfAnAct.corrigendaOfAnAct(act, act.id(), language).stream()
            .map(each ->
                new History.Corrigendum(
                    each, TheHistoryOfAnAct.changesOfAnAmendingAct(act, each, language, null)))
            .toList();
    return new ActOverTime(act, language, state, amendingActs, corrigenda);
  }

  /** Returns the amending acts of {@code act}, in their order: the newest is first. */
  public static List<ActIdentifier> amendingActsOf(Act act, Language language) {
    return TheHistoryOfAnAct.amendingActs(act, language).stream()
        .sorted(TheHistoryOfAnAct.orderOfTheAmendingActs(act, language))
        .toList();
  }

  /** Works out {@code amending}, an act that amends {@code act}, for each day. */
  public static Amending amending(Act act, ActIdentifier amending, Language language) {
    var dated = DatedAct.of(act, amending);
    var changes = TheHistoryOfAnAct.changesOfAnAmendingAct(act, amending, language, null);
    var states = changes.stream().map(change -> dated.state(ActDates.days(change))).toList();
    return new Amending(
        amending,
        TheStateOnADay.howAnAmendingActStands(states, dated.state(Set.of())),
        dated.applying(Set.of()).orElse(null),
        changes);
  }

  /**
   * Returns the history of the act on {@code day}. It applies no rule: it only reads.
   *
   * @param today today, for the tense of the words and the links
   */
  public History on(LocalDate day, LocalDate today) {
    return new History(
        act,
        language,
        day,
        null,
        today,
        state.on(day),
        amendingActs.stream().map(each -> each.on(day)).toList(),
        null,
        corrigenda);
  }
}
