// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The feature "The history of an act": one function for each of its rules, in the order of the
 * feature. A rule reads facts and gives one new fact: the act as Cellar has it, the texts that are
 * built, or a fact that an earlier rule gave. It does not call another rule: {@link ActOverTime#of}
 * applies the rules in order.
 */
public final class TheHistoryOfAnAct {

  private TheHistoryOfAnAct() {}

  /**
   * The two texts that show the changes of an amending act.
   *
   * @param first the first version that integrates the act. Null if no version does.
   * @param earlier the version before it. Null if there is none.
   */
  public record Shown(@Nullable Version first, @Nullable Version earlier) {

    /** Returns the first version that integrates the act, or empty if no version does. */
    public Optional<Version> integrating() {
      return Optional.ofNullable(first);
    }

    /** Returns the version before {@link #integrating}, or empty if there is none. */
    public Optional<Version> before() {
      return Optional.ofNullable(earlier);
    }
  }

  /**
   * The amending acts of an act: the acts that its versions in the language integrate, and the acts
   * whose amendments Cellar annotates. The act itself, the corrigenda, and an act that corrects
   * only other languages are left out.
   *
   * @return the amending acts, in no order
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-amending-acts-of-an-act")
  public static List<ActIdentifier> amendingActs(Act act, Language language) {
    return named(act, language).stream()
        .filter(each ->
            !each.equals(act.id()) && !each.corrigendum() && inLanguage(act, each, language))
        .toList();
  }

  /**
   * An amending act is newer if its first day is later: its first dated date, else the first day of
   * the first version that integrates it. An act without either is last.
   *
   * @return the order of the amending acts of {@code act}: the newest is first
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-order-of-the-amending-acts")
  public static Comparator<ActIdentifier> orderOfTheAmendingActs(Act act, Language language) {
    return Comparator.comparing(
            (ActIdentifier each) -> firstDay(act, each, language).orElse(null),
            Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
        .thenComparing(ActIdentifier::celex)
        .reversed();
  }

  /** Returns the first day of {@code amending}, by which the amending acts are in order. */
  public static Optional<LocalDate> firstDay(Act act, ActIdentifier amending, Language language) {
    return act.dates().datesOf(amending).stream()
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder())
        .or(() ->
            act.timeline(language).orElseGet(act::timeline).versions().stream()
                .filter(version -> version.integrates().contains(amending))
                .map(Version::date)
                .findFirst());
  }

  /**
   * The changes of an amending act are Cellar's amendments of it that change the text in the
   * language. They are in the order of the latest text that is built. While no text is built, they
   * are in the order of the data.
   *
   * @param latest the tags of the latest text that is built ({@link #latestBuiltText}). Null if no
   *     text is built.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-changes-of-an-amending-act")
  public static List<Amendment> changesOfAnAmendingAct(
      Act act, ActIdentifier amending, Language language, @Nullable TextTags latest) {
    var changes =
        act.amendments().stream()
            .filter(each -> each.act().equals(amending) && each.changes(language))
            .toList();
    if (latest == null) {
      return changes;
    }
    var eIds = latest.eIds();
    var occurrences = TextOccurrences.of(eIds);
    return changes.stream()
        .sorted(
            Comparator.comparing(change ->
                change
                    .location()
                    .in(occurrences)
                    .or(() -> change.location().around(occurrences))
                    .map(found -> eIds.indexOf(found.eIds().getFirst()))
                    .orElse(Integer.MAX_VALUE)))
        .toList();
  }

  /** Returns the tags of the latest text of {@code act} that is built, or empty if none is. */
  public static Optional<TextTags> latestBuiltText(
      Act act, Language language, Map<Version, TextTags> built) {
    return act.timeline(language).map(Timeline::versions).orElseGet(List::of).reversed().stream()
        .map(built::get)
        .filter(Objects::nonNull)
        .findFirst();
  }

  /**
   * The corrigenda of an act that change its text in the language, in the order of their numbers. A
   * corrigendum is published only in the languages that it corrects.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-corrigenda-of-an-act")
  public static List<ActIdentifier> corrigendaOfAnAct(
      Act act, ActIdentifier corrected, Language language) {
    return named(act, language).stream()
        .filter(each -> each.corrected().filter(corrected::equals).isPresent())
        .filter(each -> inLanguage(act, each, language))
        .filter(each ->
            act.publication(each)
                .map(publication -> publication.title(language).isPresent())
                .orElse(true))
        .sorted(Comparator.comparing(ActIdentifier::celex))
        .toList();
  }

  /**
   * Two texts show the changes of an amending act: the text of the first version that integrates
   * it, and the text of the version before that.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-two-texts-of-an-amending-act")
  public static Shown twoTextsOfAnAmendingAct(Act act, ActIdentifier amending, Language language) {
    var integrating =
        act.timeline(language).map(Timeline::versions).orElseGet(List::of).stream()
            .filter(version -> version.integrates().contains(amending))
            .findFirst();
    return new Shown(
        integrating.orElse(null),
        integrating
            .flatMap(later -> act.timeline(language).flatMap(each -> each.predecessor(later)))
            .orElse(null));
  }

  /**
   * The occurrences of a change are those of the later text: the place itself, or else the
   * occurrences that the place is in. Only if the later text does not have the place are they those
   * of the earlier text.
   *
   * @param later the occurrences of the later text
   * @param earlier the occurrences of the earlier text
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-occurrences-of-a-change")
  public static List<String> occurrencesOfAChange(
      Amendment change, List<String> later, List<String> earlier) {
    return change
        .location()
        .in(later)
        .or(() -> change.location().in(earlier))
        .map(Location.Found::eIds)
        .orElseGet(List::of);
  }

  /**
   * For a change with no tag, the data does not say which act of the version made it: the amending
   * act, or one of the other acts that its first version integrates. Corrigenda do not count.
   *
   * @param texts the two texts of {@code amending}
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-other-acts-of-its-version")
  public static List<ActIdentifier> otherActsOfItsVersion(ActIdentifier amending, Shown texts) {
    return texts.integrating().map(Version::integrates).orElseGet(List::of).stream()
        .filter(each -> !each.corrigendum() && !each.equals(amending))
        .toList();
  }

  /**
   * The acts that changed one provision: those with a change at it, at a part of it that the text
   * does not number, or at an occurrence that holds it, and those that the text tags it with. The
   * newest act is first.
   *
   * @param occurrence the provision, as an occurrence of {@code text}
   * @param text the tags of the text that the reader reads
   * @param newestFirst the order of the amending acts
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-history-of-a-provision")
  public static ProvisionHistory historyOfAProvision(
      Act act,
      Language language,
      String occurrence,
      TextTags text,
      Comparator<ActIdentifier> newestFirst) {
    var occurrences = TextOccurrences.of(text.eIds());
    var changes =
        act.amendments().stream()
            .filter(amendment -> amendment.changes(language))
            .filter(amendment ->
                amendment
                    .location()
                    .in(occurrences)
                    .filter(found ->
                        found.eIds().contains(occurrence)
                            || (!found.within()
                                && found.eIds().stream()
                                    .anyMatch(holder -> Amended.in(occurrence, holder))))
                    .isPresent())
            .sorted(Comparator.comparing(Amendment::act, newestFirst))
            .toList();
    var named = changes.stream().map(Amendment::act).collect(Collectors.toUnmodifiableSet());
    var tagging =
        text.byAct().entrySet().stream()
            .filter(each -> each.getValue().contains(occurrence))
            .map(Map.Entry::getKey)
            .filter(each -> !each.equals(act.id()) && !named.contains(each))
            .sorted(newestFirst)
            .toList();
    return new ProvisionHistory(occurrence, changes, tagging);
  }

  /**
   * The data names no act for an occurrence that is not in the act as published, if the text tags
   * no act at it, at an occurrence that holds it or at an occurrence in it, and Cellar annotates no
   * change at these occurrences.
   *
   * @param reading the version whose text the reader reads
   * @param built the tags of the texts that are built
   * @return the outer occurrences of the text for which the data names no act
   */
  @BusinessRule(
      group = RuleGroup.THE_HISTORY_OF_AN_ACT,
      name = "a-provision-the-data-names-no-act-for")
  public static List<String> aProvisionTheDataNamesNoActFor(
      Act act, Language language, Version reading, Map<Version, TextTags> built) {
    var first =
        act.timeline(language).map(Timeline::versions).orElseGet(List::of).stream().findFirst();
    var published = first.map(built::get).orElse(null);
    var text = built.get(reading);
    if (published == null
        || text == null
        || first.orElseThrow().kind() != Version.Kind.ORIGINAL
        || first.orElseThrow().equals(reading)) {
      return List.of();
    }
    var original = Set.copyOf(published.eIds());
    var said = new LinkedHashSet<>(changedOccurrences(act, text.eIds(), language).keySet());
    text.byAct().values().forEach(said::addAll);
    var holders = new LinkedHashSet<String>();
    said.forEach(each -> holders.addAll(holding(each)));
    return text.eIds().stream()
        .filter(eId -> !original.contains(eId))
        .filter(eId -> holding(eId).stream().allMatch(original::contains))
        .filter(eId -> !said.contains(eId) && !holders.contains(eId))
        .filter(eId -> holding(eId).stream().noneMatch(said::contains))
        .toList();
  }

  /**
   * The first text that has an occurrence that the data names no act for.
   *
   * @param built the tags of the texts that are built
   */
  @BusinessRule(
      group = RuleGroup.THE_HISTORY_OF_AN_ACT,
      name = "a-provision-the-data-names-no-act-for")
  public static Optional<Version> firstTextWithTheProvision(
      Act act, Language language, String occurrence, Map<Version, TextTags> built) {
    return act.timeline(language).map(Timeline::versions).orElseGet(List::of).stream()
        .filter(version ->
            Optional.ofNullable(built.get(version)).stream()
                .anyMatch(tags -> tags.eIds().contains(occurrence)))
        .findFirst();
  }

  /**
   * The amending acts between two days are those that the text of the later day includes and the
   * text of the earlier day does not.
   *
   * @param earlier the version whose text a comparison shows for the earlier day, or null if that
   *     day is before the first version
   * @param later the version whose text a comparison shows for the later day, or null
   * @param amendingActs the amending acts of the act, newest first
   * @return the amending acts between the days, newest first. Empty if one of the days is before
   *     the first version of the act.
   */
  @BusinessRule(
      group = RuleGroup.THE_HISTORY_OF_AN_ACT,
      name = "the-amending-acts-between-two-days")
  public static Optional<List<ActIdentifier>> amendingActsBetweenTwoDays(
      Act act,
      @Nullable Version earlier,
      @Nullable Version later,
      List<ActIdentifier> amendingActs) {
    if (earlier == null || later == null) {
      return Optional.empty();
    }
    var between = act.integratedBetween(earlier, later);
    return Optional.of(amendingActs.stream().filter(between::contains).toList());
  }

  /**
   * The history of a provision between two days has the amending acts between these days.
   *
   * @param provision the history of the provision
   * @param between the amending acts between the two days
   */
  @BusinessRule(
      group = RuleGroup.THE_HISTORY_OF_AN_ACT,
      name = "the-amending-acts-between-two-days")
  public static List<ActIdentifier> actsOfAProvisionBetweenTwoDays(
      ProvisionHistory provision, Collection<ActIdentifier> between) {
    return provision.acts().stream().filter(between::contains).toList();
  }

  /**
   * An amending act changes the act on each day from which a part of it applies. An act that is
   * only in force has the day of its entry into force. An act whose day Cellar does not give has no
   * day.
   *
   * @param amendingActs the amending acts of the act, newest first, with when each applies
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-days-of-change")
  public static SortedMap<LocalDate, List<ActIdentifier>> daysOfChange(
      List<ActOverTime.Amending> amendingActs) {
    var days = new TreeMap<LocalDate, List<ActIdentifier>>();
    for (var each : amendingActs.reversed()) {
      var own =
          Optional.ofNullable(each.applying())
              .map(applying ->
                  switch (applying) {
                    case Applying.From from -> List.of(from.day());
                    case Applying.FromMoreThanOneDay parts ->
                        parts.starts().stream()
                            .filter(start ->
                                !start.entryIntoForce() || start.day().equals(parts.first()))
                            .map(Applying.Start::day)
                            .toList();
                    case Applying.InForce inForce -> List.of(inForce.day());
                    case Applying.SetBy _ -> List.<LocalDate>of();
                  })
              .orElseGet(List::of);
      own.forEach(day -> days.computeIfAbsent(day, _ -> new ArrayList<>()).add(each.act()));
    }
    return days;
  }

  /** Returns the changes that Cellar annotates at each occurrence of a text with {@code eIds}. */
  public static Map<String, List<Amendment>> changedOccurrences(
      Act act, List<String> eIds, Language language) {
    var byOccurrence = new LinkedHashMap<String, List<Amendment>>();
    var occurrences = TextOccurrences.of(eIds);
    for (var amendment : act.amendments()) {
      if (amendment.changes(language)) {
        amendment
            .location()
            .in(occurrences)
            .ifPresent(found ->
                found
                    .eIds()
                    .forEach(eId ->
                        byOccurrence.computeIfAbsent(eId, _ -> new ArrayList<>()).add(amendment)));
      }
    }
    return byOccurrence;
  }

  private static List<String> holding(String eId) {
    var holders = new ArrayList<String>();
    for (var at = eId.lastIndexOf('/'); at > 0; at = eId.lastIndexOf('/', at - 1)) {
      holders.add(eId.substring(0, at));
    }
    return holders;
  }

  /** Returns the acts that the data names for {@code act}: in its versions, or in an amendment. */
  private static Set<ActIdentifier> named(Act act, Language language) {
    var found = new LinkedHashSet<ActIdentifier>();
    act.timeline(language)
        .orElseGet(act::timeline)
        .versions()
        .forEach(each -> found.addAll(each.integrates()));
    act.amendments().forEach(each -> found.add(each.act()));
    return found;
  }

  /** Returns whether {@code amending} can change the text in {@code language}. */
  static boolean inLanguage(Act act, ActIdentifier amending, Language language) {
    var own = act.amendments().stream().filter(each -> each.act().equals(amending)).toList();
    return own.isEmpty()
        || own.stream()
            .anyMatch(each -> each.languages().isEmpty() || each.languages().contains(language));
  }
}
