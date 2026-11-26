// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.LegalState;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The history of one provision of a text of an act, on a day, as the rules give it: the acts that
 * changed the provision, each with the state of its change on the day. {@link Histories} gives it.
 * A page only puts it into words.
 *
 * @param overTime what the rules tell of the act for each day
 * @param reading the version whose text has the provision
 * @param occurrence the eId of the occurrence of the text that is the provision
 * @param day the day
 * @param history the acts that changed the provision
 * @param name the name of the provision, as a change of it names it. For example, "Article 6(2)".
 *     Null if no change names it.
 * @param changes each change that Cellar annotates at the provision, newest act first
 * @param tagging each act that the text tags the provision with and that has no change, newest
 *     first
 * @param firstText the first built text that has the provision. Null if no built text has it.
 * @param integrating the first version that integrates each amending act of the history, in date
 *     order
 */
public record ProvisionOnADay(
    ActOverTime overTime,
    Version reading,
    String occurrence,
    LocalDate day,
    ProvisionHistory history,
    @Nullable String name,
    List<Change> changes,
    List<Tagging> tagging,
    @Nullable Version firstText,
    List<Version> integrating) {

  public ProvisionOnADay {
    changes = List.copyOf(changes);
    tagging = List.copyOf(tagging);
    integrating = List.copyOf(integrating);
  }

  /**
   * A change of the provision, on the day.
   *
   * @param change the change
   * @param appliesFrom the day from which it applies. Null if the data gives no one day.
   * @param state its state on the day
   * @param whole when its act applies as a whole. Null if Cellar has no dates of the act.
   */
  public record Change(
      Amendment change,
      @Nullable LocalDate appliesFrom,
      LegalState state,
      @Nullable Applying whole) {}

  /**
   * An act that the text tags the provision with, on the day. The data does not say what it did.
   *
   * @param act the act
   * @param appliesFrom the day from which the act applies. Null if the data gives no one day.
   * @param state the state of the act on the day
   * @param whole when the act applies as a whole. Null if Cellar has no dates of it.
   */
  public record Tagging(
      ActIdentifier act,
      @Nullable LocalDate appliesFrom,
      LegalState state,
      @Nullable Applying whole) {}

  /**
   * Returns the history of the occurrence {@code occurrence} of {@code text}, the text of {@code
   * reading}, a version of the act of {@code overTime}, on {@code day}.
   *
   * @param texts the tags of the built texts in the language, by version
   */
  public static ProvisionOnADay of(
      ActOverTime overTime,
      Version reading,
      String occurrence,
      TextTags text,
      LocalDate day,
      Map<Version, TextTags> texts) {
    var act = overTime.act();
    var language = overTime.language();
    var history =
        TheHistoryOfAnAct.historyOfAProvision(
            act,
            language,
            occurrence,
            text,
            TheHistoryOfAnAct.orderOfTheAmendingActs(act, language));
    var changes = new ArrayList<Change>();
    for (var change : history.changes()) {
      var dated = DatedAct.of(act, change.act());
      var days = ActDates.days(change);
      changes.add(
          new Change(
              change,
              act.dates().changeAppliesFrom(change.act(), days).orElse(null),
              dated.state(days).on(day),
              dated.applying(Set.of()).orElse(null)));
    }
    var tagging =
        history.tagging().stream()
            .map(each -> {
              var dated = DatedAct.of(act, each);
              return new Tagging(
                  each,
                  act.dates().changeAppliesFrom(each, Set.of()).orElse(null),
                  dated.state(Set.of()).on(day),
                  dated.applying(Set.of()).orElse(null));
            })
            .toList();
    return new ProvisionOnADay(
        overTime,
        reading,
        occurrence,
        day,
        history,
        name(history, occurrence, text),
        changes,
        tagging,
        TheHistoryOfAnAct.firstTextWithTheProvision(act, language, occurrence, texts).orElse(null),
        history.acts().stream()
            .filter(each -> !each.corrigendum())
            .flatMap(each ->
                TheHistoryOfAnAct.twoTextsOfAnAmendingAct(act, each, language)
                    .integrating()
                    .stream())
            .sorted(Comparator.comparing(Version::date))
            .toList());
  }

  /**
   * Returns the occurrences of the text of {@code reading} that have a history to show, by their
   * eIds: the occurrences with a change of their own, and the occurrences that the data names no
   * act for. Empty if the text of {@code reading} is not built.
   */
  public static List<String> withAHistory(
      Act act, Language language, Version reading, Map<Version, TextTags> texts) {
    var text = texts.get(reading);
    if (text == null) {
      return List.of();
    }
    var occurrences =
        new ArrayList<>(TheHistoryOfAnAct.changedOccurrences(act, text.eIds(), language).keySet());
    occurrences.addAll(
        TheHistoryOfAnAct.aProvisionTheDataNamesNoActFor(act, language, reading, texts));
    return occurrences;
  }

  /** Returns the amending acts of the history, without its corrigenda. The newest is first. */
  public List<ActIdentifier> amendingActs() {
    return history.acts().stream().filter(each -> !each.corrigendum()).toList();
  }

  private static @Nullable String name(ProvisionHistory history, String occurrence, TextTags text) {
    return history.changes().stream()
        .filter(change ->
            change
                .location()
                .in(text.eIds())
                .filter(found -> !found.within() && found.eIds().equals(List.of(occurrence)))
                .isPresent())
        .map(change -> change.place().words())
        .filter(each -> !each.isEmpty())
        .findFirst()
        .orElse(null);
  }
}
