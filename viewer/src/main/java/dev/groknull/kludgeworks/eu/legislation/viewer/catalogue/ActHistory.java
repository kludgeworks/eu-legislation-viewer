// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The history of an act: its amending acts, newest first, the changes of each, its corrigenda, the
 * two texts that show the changes of an amending act, and the days on which an amending act changes
 * it. The rules are in the feature "The history of an act".
 */
public final class ActHistory {
  private final Act act;

  private ActHistory(Act act) {
    this.act = act;
  }

  /** Returns what {@code act} tells of this. */
  public static ActHistory of(Act act) {
    return new ActHistory(act);
  }

  /**
   * Returns the acts that amend the act and change its text in {@code language}, newest first.
   * These are the acts that its versions in {@code language} integrate, and the acts whose
   * amendments Cellar annotates. An act can be in no version yet.
   *
   * <ul>
   *   <li>This act and the corrigenda are left out. A corrigendum is not an amending act.
   *   <li>An act that corrects only other languages is left out. For example, 2017/837 corrects
   *       only the Swedish and the Polish text of 2015/1998.
   *   <li>An act is newer if its first dated date is later. An act without a dated date has the
   *       first day of the first version that integrates it. An act without either is last.
   * </ul>
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-amending-acts-of-an-act")
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-order-of-the-amending-acts")
  public List<ActIdentifier> amendingActs(Language language) {
    var found = new LinkedHashSet<ActIdentifier>();
    act.timeline(language)
        .orElseGet(act::timeline)
        .versions()
        .forEach(each -> found.addAll(each.integrates()));
    act.amendments().forEach(each -> found.add(each.act()));
    Comparator<ActIdentifier> newest =
        Comparator.comparing(
            (ActIdentifier each) -> firstDay(each, language).orElse(null),
            Comparator.nullsFirst(Comparator.naturalOrder()));
    return found.stream()
        .filter(each -> !each.equals(act.id()) && !each.corrigendum() && inLanguage(each, language))
        .sorted(newest.thenComparing(ActIdentifier::celex).reversed())
        .toList();
  }

  /**
   * Returns the amending acts between two days, newest first. They are the acts of {@link
   * #amendingActs(Language)} that the text of {@code to} includes and the text of {@code from} does
   * not: {@link LawOnADay#textOn} gives the two texts, and {@link Act#integratedBetween} the acts.
   * A comparison of the two days shows the changes of these acts, so the history of two days and
   * the comparison name the same acts.
   *
   * <p>Returns empty if one of the days is before the first version of the act.
   *
   * @param texts the tags of the texts in {@code language} that are built, as {@link
   *     LawOnADay#textOn} takes them
   */
  @BusinessRule(
      group = RuleGroup.THE_HISTORY_OF_AN_ACT,
      name = "the-amending-acts-between-two-days")
  public Optional<List<ActIdentifier>> amendingActsBetween(
      LocalDate from, LocalDate to, Language language, Map<Version, TextTags> texts) {
    var earlier = LawOnADay.of(act).textOn(from, language, texts);
    var later = LawOnADay.of(act).textOn(to, language, texts);
    if (earlier.isEmpty() || later.isEmpty()) {
      return Optional.empty();
    }
    var between = act.integratedBetween(earlier.get(), later.get());
    return Optional.of(amendingActs(language).stream().filter(between::contains).toList());
  }

  /**
   * Returns the days on which an amending act changes the act, in order, each with its acts. A
   * calendar marks these days.
   *
   * <ul>
   *   <li>An act that applies from one day ({@link ActDates#applying}) changes the act on that day.
   *   <li>An act that applies from more than one day changes it on each day from which a part
   *       applies. Its entry into force is such a day only if it is the first day from which a part
   *       applies.
   *   <li>An act that is in force, where Cellar does not tell from when it applies, has the day of
   *       its entry into force.
   *   <li>An act that applies from a day that Cellar does not know has no day. An act without dates
   *       has none.
   * </ul>
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-days-of-change")
  public SortedMap<LocalDate, List<ActIdentifier>> changeDays(Language language) {
    var days = new TreeMap<LocalDate, List<ActIdentifier>>();
    for (var each : amendingActs(language).reversed()) {
      var own =
          act.dates()
              .applying(each, Set.of())
              .map(
                  applying ->
                      switch (applying) {
                        case Applying.From from -> List.of(from.day());
                        case Applying.FromMoreThanOneDay parts ->
                            parts.starts().stream()
                                .filter(
                                    start ->
                                        !start.entryIntoForce()
                                            || start.day().equals(parts.first()))
                                .map(Applying.Start::day)
                                .toList();
                        case Applying.InForce inForce -> List.of(inForce.day());
                        case Applying.SetBy _ -> List.<LocalDate>of();
                      })
              .orElseGet(List::of);
      own.forEach(day -> days.computeIfAbsent(day, _ -> new ArrayList<>()).add(each));
    }
    return days;
  }

  /**
   * Returns the first version in {@code language} that integrates {@code amending}. The text of
   * this version is the first that has the changes of the act. The text of the version before it is
   * the text before the act. Returns empty if no version in the language integrates the act.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-two-texts-of-an-amending-act")
  public Optional<Version> integrating(ActIdentifier amending, Language language) {
    return act.timeline(language).map(Timeline::versions).orElseGet(List::of).stream()
        .filter(version -> version.integrates().contains(amending))
        .findFirst();
  }

  /**
   * Returns the version in {@code language} before the first version that integrates {@code
   * amending} ({@link #integrating}): its text is the text before the act. Returns empty if no
   * version integrates the act, or if the first that does is the first version.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-two-texts-of-an-amending-act")
  public Optional<Version> beforeIntegrating(ActIdentifier amending, Language language) {
    return integrating(amending, language)
        .flatMap(later -> act.timeline(language).flatMap(each -> each.predecessor(later)));
  }

  /**
   * Returns the other acts that the first version with {@code amending} integrates, in {@code
   * language}, without corrigenda. The text tags most changes with the act that made them. For a
   * change with no tag, the data does not say which of these acts or {@code amending} made it.
   * Returns empty if no version integrates {@code amending}, or if it is the only act of its
   * version.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-other-acts-of-its-version")
  public List<ActIdentifier> integratedWith(ActIdentifier amending, Language language) {
    return integrating(amending, language).map(Version::integrates).orElseGet(List::of).stream()
        .filter(each -> !each.corrigendum() && !each.equals(amending))
        .toList();
  }

  /**
   * Returns the occurrences of {@code change} in the comparison of two texts, by their eIds. {@code
   * later} are the occurrences of the first text with the changes of the act, and {@code earlier}
   * are the occurrences of the text before it, each in document order.
   *
   * <p>The occurrences are those of the later text: the place itself, or else the occurrences that
   * the place is in, for example the article of a sentence. The converter keeps a deleted
   * occurrence, so the later text has the place of a deletion too. Only if the later text does not
   * have the place at all are the occurrences those of the earlier text. Returns empty if neither
   * text has the place.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-occurrences-of-a-change")
  public static List<String> occurrences(
      Amendment change, List<String> later, List<String> earlier) {
    return change
        .location()
        .in(later)
        .or(() -> change.location().in(earlier))
        .map(Location.Found::eIds)
        .orElseGet(List::of);
  }

  /**
   * Returns the first day of {@code amending}: its first dated date, which is usually its entry
   * into force. If Cellar has no dated date of it, it is the first day of the first version in
   * {@code language} that integrates it. Returns empty if there is neither.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-order-of-the-amending-acts")
  public Optional<LocalDate> firstDay(ActIdentifier amending, Language language) {
    return act.dates().datesOf(amending).stream()
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder())
        .or(
            () ->
                act.timeline(language).orElseGet(act::timeline).versions().stream()
                    .filter(version -> version.integrates().contains(amending))
                    .map(Version::date)
                    .findFirst());
  }

  /**
   * Returns {@code changes} in the order of a text. {@code eIds} are the occurrences of the text,
   * in document order. A change has the position of its place. A place that the text does not have,
   * for example a deleted provision, has the position of the occurrence that holds it. A change
   * with no position is last.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-changes-of-an-amending-act")
  public static List<Amendment> inTextOrder(List<Amendment> changes, List<String> eIds) {
    var occurrences = Location.occurrences(eIds);
    return changes.stream()
        .sorted(
            Comparator.comparing(
                change ->
                    change
                        .location()
                        .in(occurrences)
                        .or(() -> change.location().around(occurrences))
                        .map(found -> eIds.indexOf(found.eIds().getFirst()))
                        .orElse(Integer.MAX_VALUE)))
        .toList();
  }

  /**
   * Returns the corrigenda of {@code corrected} that change the text of the act in {@code
   * language}, in the order of their numbers. {@code corrected} is the act or an act that amends
   * it. A corrigendum is published only in the languages that it corrects. So a corrigendum whose
   * publication has no title in {@code language} is left out.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-corrigenda-of-an-act")
  public List<ActIdentifier> corrigendaOf(ActIdentifier corrected, Language language) {
    var found = new LinkedHashSet<ActIdentifier>();
    act.timeline(language)
        .orElseGet(act::timeline)
        .versions()
        .forEach(each -> found.addAll(each.integrates()));
    act.amendments().forEach(each -> found.add(each.act()));
    return found.stream()
        .filter(each -> each.corrected().filter(corrected::equals).isPresent())
        .filter(each -> inLanguage(each, language))
        .filter(
            each ->
                act.publication(each)
                    .map(publication -> publication.title(language).isPresent())
                    .orElse(true))
        .sorted(Comparator.comparing(ActIdentifier::celex))
        .toList();
  }

  /**
   * Returns whether {@code amending} can change the text in {@code language}. It cannot if each of
   * its amendments is for other languages only.
   */
  boolean inLanguage(ActIdentifier amending, Language language) {
    var own = act.amendments().stream().filter(each -> each.act().equals(amending)).toList();
    return own.isEmpty()
        || own.stream()
            .anyMatch(each -> each.languages().isEmpty() || each.languages().contains(language));
  }

  /**
   * Returns the changes that {@code amending} makes to the text of the act in {@code language}, as
   * Cellar annotates them. Each change has one place.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-changes-of-an-amending-act")
  public List<Amendment> changesBy(ActIdentifier amending, Language language) {
    return act.amendments().stream()
        .filter(each -> each.act().equals(amending) && each.changes(language))
        .toList();
  }

  /**
   * Returns the changes of {@code amending} in {@code language} ({@link #changesBy}) in the order
   * of {@code latest}, the latest built text ({@link #latestText}): {@link #inTextOrder}. While no
   * text is built, {@code latest} is null, and the changes are in the order of the data.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-changes-of-an-amending-act")
  public List<Amendment> changesInTextOrder(
      ActIdentifier amending, Language language, @Nullable TextTags latest) {
    var changes = changesBy(amending, language);
    return latest == null ? changes : inTextOrder(changes, latest.eIds());
  }

  /**
   * Returns the tags of the latest text in {@code language} that is built. Its order is the order
   * of the changes of an amending act ({@link #changesInTextOrder}). {@code texts} are the tags of
   * the texts in {@code language} that are built. Returns empty if none is built.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-changes-of-an-amending-act")
  public Optional<TextTags> latestText(Language language, Map<Version, TextTags> texts) {
    return act.timeline(language).map(Timeline::versions).orElseGet(List::of).reversed().stream()
        .map(texts::get)
        .filter(Objects::nonNull)
        .findFirst();
  }

  /**
   * Returns how {@code amending} stands on {@code day}: the number of its changes to the text in
   * {@code language} in each state, {@link ActDates#stateOn}. If Cellar lists no changed place for
   * it, the act counts as one change, with the state of the whole act.
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "how-an-amending-act-stands")
  public Standing standingOn(ActIdentifier amending, Language language, LocalDate day) {
    var changes = changesBy(amending, language);
    var states =
        changes.isEmpty()
            ? List.of(act.dates().stateOn(amending, Set.of(), day))
            : changes.stream()
                .map(each -> act.dates().stateOn(amending, ActDates.days(each), day))
                .toList();
    return new Standing(
        states.contains(State.NOT_IN_FORCE),
        count(states, State.APPLIES),
        count(states, State.NOT_APPLYING),
        count(states, State.NOT_KNOWN));
  }

  private static int count(List<State> states, State state) {
    return (int) states.stream().filter(state::equals).count();
  }
}
