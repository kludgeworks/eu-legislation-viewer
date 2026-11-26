// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.LegalState;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.Stands;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * One amending act in the history of an act, on a day, as the rules give it: how it stands, when it
 * applies, and each of its changes with its state. {@link Histories#amending} gives it. The page of
 * the amending act puts it into words, and applies no rule.
 *
 * @param act the act
 * @param amending the act that amends it
 * @param language the language of the text whose changes count
 * @param day the day
 * @param today today, for the tense of the words and the links
 * @param latest the tags of the latest text of the act that is built: the changes have its order.
 *     Null if no text is built.
 * @param texts the two texts that show the wording of the changes. Null if no version integrates
 *     the amending act, or if one of the two texts is not built.
 * @param stands how the amending act stands on the day
 * @param applying when the amending act applies, as its dates tell. Null if Cellar has no dates of
 *     it.
 * @param changes its changes to the text in the language, in the order of the latest text that is
 *     built, else in the order of the data
 * @param parts the changes by the day from which they apply, in the order of the days. The changes
 *     with no such day are last.
 * @param otherActs the other acts that the first version with the amending act integrates
 * @param corrigenda the corrigenda of the amending act that correct the text in the language
 * @param overTime what the rules tell of the act for each day
 */
public record AmendingHistory(
    Act act,
    ActIdentifier amending,
    Language language,
    LocalDate day,
    LocalDate today,
    @Nullable TextTags latest,
    @Nullable TwoTexts texts,
    Stands stands,
    @Nullable Applying applying,
    List<Change> changes,
    List<Part> parts,
    List<ActIdentifier> otherActs,
    List<History.Corrigendum> corrigenda,
    ActOverTime overTime) {

  public AmendingHistory {
    changes = List.copyOf(changes);
    parts = List.copyOf(parts);
    otherActs = List.copyOf(otherActs);
    corrigenda = List.copyOf(corrigenda);
  }

  /**
   * The two texts that show the wording of the changes of an amending act, with their tags.
   *
   * @param earlier the version before the first version that integrates the act
   * @param later the first version that integrates the act
   * @param before the tags of the text of {@code earlier}
   * @param after the tags of the text of {@code later}
   */
  public record TwoTexts(Version earlier, Version later, TextTags before, TextTags after) {}

  /**
   * One change of the amending act, on the day.
   *
   * @param change the change
   * @param appliesFrom the day from which it applies. Null if the data does not give one day.
   * @param state its state on the day
   * @param occurrences the occurrences of the two texts that are the change. Empty if the two texts
   *     are not built.
   */
  public record Change(
      Amendment change,
      @Nullable LocalDate appliesFrom,
      LegalState state,
      List<String> occurrences) {
    public Change {
      occurrences = List.copyOf(occurrences);
    }
  }

  /**
   * The changes that apply from one day.
   *
   * @param from the day. Null for the changes for which the data does not give one day.
   * @param changes how many changes apply from it
   * @param state the state on the day of a change that applies from {@code from}. Null if {@code
   *     from} is null.
   */
  public record Part(@Nullable LocalDate from, int changes, @Nullable LegalState state) {}

  /**
   * Returns {@code amending} in the history of {@code act} on {@code day}.
   *
   * @param built the tags of the texts of the act in {@code language} that are built
   */
  public static AmendingHistory of(
      Act act,
      ActIdentifier amending,
      Language language,
      LocalDate day,
      LocalDate today,
      Map<Version, TextTags> built) {
    var latest = TheHistoryOfAnAct.latestBuiltText(act, language, built).orElse(null);
    var shown = TheHistoryOfAnAct.twoTextsOfAnAmendingAct(act, amending, language);
    var texts = texts(shown, built).orElse(null);
    var dated = DatedAct.of(act, amending);
    var changes =
        TheHistoryOfAnAct.changesOfAnAmendingAct(act, amending, language, latest).stream()
            .map(change -> change(act, dated, change, day, texts))
            .toList();
    return new AmendingHistory(
        act,
        amending,
        language,
        day,
        today,
        latest,
        texts,
        ActOverTime.amending(act, amending, language).stands().on(day),
        dated.applying(Set.of()).orElse(null),
        changes,
        parts(dated, changes, day),
        TheHistoryOfAnAct.otherActsOfItsVersion(amending, shown),
        TheHistoryOfAnAct.corrigendaOfAnAct(act, amending, language).stream()
            .map(each ->
                new History.Corrigendum(
                    each, TheHistoryOfAnAct.changesOfAnAmendingAct(act, each, language, null)))
            .toList(),
        ActOverTime.of(act, language));
  }

  private static Change change(
      Act act, DatedAct dated, Amendment change, LocalDate day, @Nullable TwoTexts texts) {
    var days = ActDates.days(change);
    return new Change(
        change,
        act.dates().changeAppliesFrom(dated.id(), days).orElse(null),
        dated.state(days).on(day),
        texts == null
            ? List.of()
            : TheHistoryOfAnAct.occurrencesOfAChange(
                change, texts.after().eIds(), texts.before().eIds()));
  }

  private static List<Part> parts(DatedAct dated, List<Change> changes, LocalDate day) {
    var counts =
        new TreeMap<Optional<LocalDate>, Integer>(
            Comparator.comparing((Optional<LocalDate> each) -> each.orElse(LocalDate.MAX)));
    changes.forEach(
        change -> counts.merge(Optional.ofNullable(change.appliesFrom()), 1, Integer::sum));
    return counts.entrySet().stream()
        .map(each ->
            new Part(
                each.getKey().orElse(null),
                each.getValue(),
                each.getKey().map(from -> dated.state(Set.of(from)).on(day)).orElse(null)))
        .toList();
  }

  private static Optional<TwoTexts> texts(
      TheHistoryOfAnAct.Shown shown, Map<Version, TextTags> built) {
    return shown
        .before()
        .flatMap(earlier ->
            shown
                .integrating()
                .flatMap(later ->
                    Optional.ofNullable(built.get(earlier))
                        .flatMap(before ->
                            Optional.ofNullable(built.get(later))
                                .map(after -> new TwoTexts(earlier, later, before, after)))));
  }
}
