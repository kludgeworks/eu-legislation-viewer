// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.LegalState;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.Stands;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The history of an act on a day, or between two days, as the rules give it: the state of the act
 * itself, its amending acts with how each stands, and its corrigenda. {@link Histories} gives it. A
 * page and the API show the same history: each only puts it into its own form.
 *
 * @param act the act
 * @param language the language of the text whose changes count
 * @param day the day. With two days, it is the second day.
 * @param first the first of two days, before {@code day}. Null for one day.
 * @param today today, for the tense of the words and the links
 * @param state the state of the act itself on {@code day}. Null with two days.
 * @param amendingActs each amending act of the act, newest first, as it stands on {@code day}
 * @param between the amending acts between the two days. Null with one day.
 * @param corrigenda the corrigenda of the act that correct the text in the language
 */
public record History(
    Act act,
    Language language,
    LocalDate day,
    @Nullable LocalDate first,
    LocalDate today,
    @Nullable LegalState state,
    List<AmendingAct> amendingActs,
    @Nullable Between between,
    List<Corrigendum> corrigenda) {

  public History {
    amendingActs = List.copyOf(amendingActs);
    corrigenda = List.copyOf(corrigenda);
  }

  /**
   * Returns this history as the history of two days: {@code first} and the day of this history. It
   * has the amending acts between the two days.
   *
   * @param built the tags of the texts of the act in the language that are built
   */
  public History between(LocalDate first, Map<Version, TextTags> built) {
    var found =
        TheHistoryOfAnAct.amendingActsBetweenTwoDays(
            act, language, first, day, amendingActs.stream().map(AmendingAct::act).toList(), built);
    var between = found.map(Set::copyOf).orElseGet(Set::of);
    return new History(
        act,
        language,
        day,
        first,
        today,
        null,
        amendingActs,
        new Between(
            found.isPresent(),
            amendingActs.stream().filter(each -> between.contains(each.act())).toList()),
        corrigenda);
  }

  /** Returns the amending acts that the history is of: those between the two days, else all. */
  public List<AmendingAct> shown() {
    return between == null ? amendingActs : between.acts();
  }

  /**
   * An amending act, as it stands on the day.
   *
   * @param act the amending act
   * @param stands its state as a whole, and how many of its changes are in each state
   * @param applying when it applies, as its dates tell. Null if Cellar has no dates of it.
   * @param changes its changes to the text in the language
   */
  public record AmendingAct(
      ActIdentifier act, Stands stands, @Nullable Applying applying, List<Amendment> changes) {
    public AmendingAct {
      changes = List.copyOf(changes);
    }
  }

  /**
   * The amending acts between two days.
   *
   * @param textOnTheFirstDay whether the act has a text on the first day. If not, no acts are
   *     between the days.
   * @param acts the amending acts between the days, newest first
   */
  public record Between(boolean textOnTheFirstDay, List<AmendingAct> acts) {
    public Between {
      acts = List.copyOf(acts);
    }
  }

  /**
   * A corrigendum of the act.
   *
   * @param act the corrigendum
   * @param changes the places that it corrects in the text in the language
   */
  public record Corrigendum(ActIdentifier act, List<Amendment> changes) {
    public Corrigendum {
      changes = List.copyOf(changes);
    }
  }
}
