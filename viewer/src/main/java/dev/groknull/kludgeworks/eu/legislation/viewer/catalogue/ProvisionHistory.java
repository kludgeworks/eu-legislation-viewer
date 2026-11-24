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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The history of one provision of an act: the acts that changed it. {@link #of} makes it.
 *
 * @param occurrence the eId of the occurrence of the text that is the provision
 * @param changes the changes that Cellar annotates at the provision, in a part of it that the text
 *     does not number, or at an occurrence that holds it. The newest act is first.
 * @param tagging the acts that the text tags the provision with, and that have no change in {@code
 *     changes}. The data does not say what each of them did. The newest act is first.
 */
@ValueObject
public record ProvisionHistory(
    String occurrence, List<Amendment> changes, List<ActIdentifier> tagging) {

  public ProvisionHistory {
    changes = List.copyOf(changes);
    tagging = List.copyOf(tagging);
  }

  /** Returns the acts that changed the provision, each one time. The newest act is first. */
  public List<ActIdentifier> acts() {
    var acts = new LinkedHashSet<ActIdentifier>();
    changes.forEach(change -> acts.add(change.act()));
    acts.addAll(tagging);
    return List.copyOf(acts);
  }

  /**
   * Returns the acts that changed the provision and that are among {@code acts}, for example the
   * amending acts between two days ({@link ActHistory#amendingActsBetween}). The newest act is
   * first.
   */
  @BusinessRule(
      group = RuleGroup.THE_HISTORY_OF_AN_ACT,
      name = "the-amending-acts-between-two-days")
  public List<ActIdentifier> actsAmong(Collection<ActIdentifier> acts) {
    return acts().stream().filter(acts::contains).toList();
  }

  /**
   * Returns the occurrences of a text that have a change of their own, by their eIds, each with its
   * changes in {@code language}. {@code eIds} are the occurrences of the text, in document order. A
   * change is at the occurrence that is its place. If its place is finer than the occurrences, for
   * example a sentence, it is at the occurrence that the place is in. A change whose place the text
   * does not have is at no occurrence.
   */
  public static Map<String, List<Amendment>> changedOccurrences(
      Act act, List<String> eIds, Language language) {
    var byOccurrence = new LinkedHashMap<String, List<Amendment>>();
    var occurrences = TextOccurrences.of(eIds);
    for (var amendment : act.amendments()) {
      if (!amendment.changes(language)) {
        continue;
      }
      amendment
          .location()
          .in(occurrences)
          .ifPresent(
              found ->
                  found
                      .eIds()
                      .forEach(
                          eId ->
                              byOccurrence
                                  .computeIfAbsent(eId, occurrence -> new ArrayList<>())
                                  .add(amendment)));
    }
    return byOccurrence;
  }

  /**
   * Returns the history of the provision that is the occurrence {@code occurrence} of a text: the
   * acts that changed it. {@code text} are the tags of the text.
   *
   * <ul>
   *   <li>A change at the occurrence is a change of the provision. So is a change at a part of it
   *       that the text does not number, for example a sentence.
   *   <li>A change at an occurrence that holds the provision is a change of the provision. For
   *       example, an act that replaces Article 6 changes Article 6(2).
   *   <li>A change at an occurrence in the provision is not in its history. That occurrence has its
   *       own history.
   *   <li>An act that the text tags the occurrence with changed the provision, also if Cellar
   *       annotates no change of it there.
   * </ul>
   *
   * <p>The newest act is first: the act with the latest first day, {@link ActHistory#firstDay}.
   */
  @BusinessRule(group = RuleGroup.THE_HISTORY_OF_AN_ACT, name = "the-history-of-a-provision")
  public static ProvisionHistory of(Act act, String occurrence, TextTags text, Language language) {
    Comparator<ActIdentifier> newest =
        Comparator.comparing(
                (ActIdentifier each) -> ActHistory.of(act).firstDay(each, language).orElse(null),
                Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(ActIdentifier::celex)
            .reversed();
    var occurrences = TextOccurrences.of(text.eIds());
    var changes =
        act.amendments().stream()
            .filter(amendment -> amendment.changes(language))
            .filter(
                amendment ->
                    amendment
                        .location()
                        .in(occurrences)
                        .filter(
                            found ->
                                found.eIds().contains(occurrence)
                                    || (!found.within()
                                        && found.eIds().stream()
                                            .anyMatch(holder -> Amended.in(occurrence, holder))))
                        .isPresent())
            .sorted(Comparator.comparing(Amendment::act, newest))
            .toList();
    var named = changes.stream().map(Amendment::act).collect(Collectors.toSet());
    var tagging =
        text.byAct().entrySet().stream()
            .filter(each -> each.getValue().contains(occurrence))
            .map(Map.Entry::getKey)
            .filter(each -> !each.equals(act.id()) && !named.contains(each))
            .sorted(newest)
            .toList();
    return new ProvisionHistory(occurrence, changes, tagging);
  }

  /**
   * Returns the occurrences of the text of {@code reading} that are not in the act as published,
   * where the data does not say which act inserted them. {@code texts} are the tags of the built
   * texts in {@code language}.
   *
   * <p>The data says nothing of an occurrence if the text tags no act at it, at an occurrence that
   * holds it or at an occurrence in it, and Cellar annotates no change at these occurrences. Of
   * such occurrences, only the outer occurrence is returned: an occurrence in it is not. Returns
   * none if the text of the act as published or the text of {@code reading} is not built.
   */
  @BusinessRule(
      group = RuleGroup.THE_HISTORY_OF_AN_ACT,
      name = "a-provision-the-data-names-no-act-for")
  public static List<String> unattributed(
      Act act, Version reading, Language language, Map<Version, TextTags> texts) {
    var first =
        act.timeline(language).map(Timeline::versions).orElseGet(List::of).stream().findFirst();
    var published = first.map(texts::get).orElse(null);
    var text = texts.get(reading);
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
   * Returns the first version in {@code language} whose text has the occurrence {@code occurrence}.
   * {@code texts} are the tags of the built texts. Returns empty if no built text has the
   * occurrence.
   */
  @BusinessRule(
      group = RuleGroup.THE_HISTORY_OF_AN_ACT,
      name = "a-provision-the-data-names-no-act-for")
  public static Optional<Version> firstWith(
      Act act, String occurrence, Language language, Map<Version, TextTags> texts) {
    return act.timeline(language).map(Timeline::versions).orElseGet(List::of).stream()
        .filter(
            version ->
                Optional.ofNullable(texts.get(version)).stream()
                    .anyMatch(tags -> tags.eIds().contains(occurrence)))
        .findFirst();
  }

  /**
   * Returns the eIds of the occurrences that hold the occurrence {@code eId}, as its eId names
   * them.
   */
  private static List<String> holding(String eId) {
    var holders = new ArrayList<String>();
    for (var at = eId.lastIndexOf('/'); at > 0; at = eId.lastIndexOf('/', at - 1)) {
      holders.add(eId.substring(0, at));
    }
    return holders;
  }
}
