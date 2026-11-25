// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline.TwoDays;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline.TwoVersions;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import org.jspecify.annotations.Nullable;

/**
 * The feature "What a comparison covers": one function for each of its rules, in the order of the
 * feature. A rule reads facts and gives one new fact. It does not call another rule: {@link
 * Timeline} and {@link Inclusions} apply the rules.
 */
public final class WhatAComparisonCovers {

  private WhatAComparisonCovers() {}

  /**
   * The two days of a comparison come from the days that the reader gives, in this order:
   *
   * <ol>
   *   <li>A day that the reader gives stays as given: it need not be the first day of a version.
   *   <li>Without To, To is the first day of the version that applies on the day that the reader
   *       asks. If no version applies on that day, To is the first day of the latest version.
   *   <li>Without From, From is the first day of the version before the version of To. If the
   *       version of To is the first version, From is the day of To, and To is the first day of the
   *       version after it.
   *   <li>If From is after To, the two days change places.
   * </ol>
   *
   * @param timeline the versions of the act in one language
   * @param from the day From that the reader gives, or null
   * @param to the day To that the reader gives, or null
   * @param today the day on which the reader asks
   */
  @BusinessRule(group = RuleGroup.WHAT_A_COMPARISON_COVERS, name = "the-two-days-of-a-comparison")
  public static TwoDays theTwoDaysOfAComparison(
      Timeline timeline, @Nullable LocalDate from, @Nullable LocalDate to, LocalDate today) {
    var later = to != null ? to : timeline.applicableOn(today).orElseGet(timeline::latest).date();
    var earlier = from;
    if (earlier == null) {
      var version = timeline.applicableOn(later);
      var predecessor = version.flatMap(timeline::predecessor);
      if (version.isEmpty() || predecessor.isPresent()) {
        earlier = predecessor.map(Version::date).orElse(later);
      } else {
        earlier = later;
        later = timeline.successor(version.get()).map(Version::date).orElse(later);
      }
    }
    return earlier.isAfter(later) ? new TwoDays(later, earlier) : new TwoDays(earlier, later);
  }

  /**
   * The viewer compares the law on any two days at the versions that apply on them.
   *
   * @param ofFrom the version that applies on the earlier day, or null if none does
   * @param ofTo the version that applies on the later day, or null if none does
   * @return the two versions. Empty if no version applies on one of the days.
   */
  @BusinessRule(group = RuleGroup.WHAT_A_COMPARISON_COVERS, name = "two-days-at-their-versions")
  public static Optional<TwoVersions> twoDaysAtTheirVersions(
      @Nullable Version ofFrom, @Nullable Version ofTo) {
    return ofFrom == null || ofTo == null
        ? Optional.empty()
        : Optional.of(new TwoVersions(ofFrom, ofTo));
  }

  /**
   * A comparison is between the first days of two versions, the earlier first.
   *
   * @param from the earlier day
   * @param to the later day
   * @param startsOnFrom the version that starts on {@code from}, or null if none does
   * @param startsOnTo the version that starts on {@code to}, or null if none does
   * @return the two versions. Empty if a day is not the first day of a version, or if {@code from}
   *     is after {@code to}.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "a-comparison-is-of-two-versions",
      revision = 2)
  public static Optional<TwoVersions> aComparisonIsOfTwoVersions(
      LocalDate from, LocalDate to, @Nullable Version startsOnFrom, @Nullable Version startsOnTo) {
    return from.isAfter(to) || startsOnFrom == null || startsOnTo == null
        ? Optional.empty()
        : Optional.of(new TwoVersions(startsOnFrom, startsOnTo));
  }

  /**
   * In one language, at most one version starts on a day. The original text and a consolidation can
   * start on the same day in different languages. The version of a day is the one in the reader's
   * language. If none is in that language, it is the first of them.
   *
   * @param ofTheDay the versions that start on one day. There is one at least.
   * @param language the language of the reader
   */
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "one-version-a-day-in-each-language")
  public static Version oneVersionADayInEachLanguage(List<Version> ofTheDay, Language language) {
    return ofTheDay.stream()
        .filter(version -> version.languages().contains(language))
        .findFirst()
        .orElseGet(ofTheDay::getFirst);
  }

  /**
   * A version includes the acts that Cellar lists for it, and the acts that its own text tags
   * first: no earlier version includes them, by Cellar's list or by the tags of an earlier text
   * that the viewer builds. If the viewer does not build a text, only Cellar's list counts for it.
   *
   * <p>For example, the version of 767/2008 of 29 December 2017 tags 16 occurrences with 2017/2226,
   * but Cellar lists 2017/2226 only from the version of 11 June 2019.
   *
   * @param versions the versions, in order
   * @param texts the tags of the texts that are built, by version
   * @return the acts that the text of each version tags first, by version. Cellar lists none of
   *     them for that version.
   */
  @BusinessRule(group = RuleGroup.WHAT_A_COMPARISON_COVERS, name = "the-acts-a-version-includes")
  public static Map<Version, List<ActIdentifier>> theActsAVersionIncludes(
      List<Version> versions, Map<Version, TextTags> texts) {
    var included = new HashSet<ActIdentifier>();
    var tagged = new HashMap<Version, List<ActIdentifier>>();
    for (var version : versions) {
      included.addAll(version.integrates());
      var text = texts.get(version);
      if (text == null) {
        continue;
      }
      var first =
          text.byAct().keySet().stream()
              .filter(each -> !included.contains(each))
              .sorted(Comparator.comparing(ActIdentifier::celex))
              .toList();
      if (!first.isEmpty()) {
        tagged.put(version, first);
        included.addAll(first);
      }
    }
    return tagged;
  }

  /**
   * The acts between two versions are the acts that the versions after From include, up to and with
   * To, in the order of the versions. Corrigenda are among them.
   *
   * @param versions the versions, in order
   * @param included what each version includes
   * @param from the earlier version
   * @param to the later version
   */
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "the-acts-between-two-versions",
      revision = 2)
  public static SequencedSet<ActIdentifier> theActsBetweenTwoVersions(
      List<Version> versions, Inclusions included, Version from, Version to) {
    var between = new LinkedHashSet<ActIdentifier>();
    for (var version : versions) {
      if (version.date().isAfter(from.date()) && !version.date().isAfter(to.date())) {
        between.addAll(included.of(version));
      }
    }
    return between;
  }

  /**
   * A comparison can show the changes of one act only if that act is between its two versions.
   *
   * @param between the acts between the two versions
   * @param celex the CELEX number of the act
   * @return the act, or empty if it is not between the versions
   */
  @BusinessRule(group = RuleGroup.WHAT_A_COMPARISON_COVERS, name = "one-act-between-the-versions")
  public static Optional<ActIdentifier> oneActBetweenTheVersions(
      SequencedSet<ActIdentifier> between, String celex) {
    return between.stream().filter(each -> each.celex().equals(celex)).findFirst();
  }
}
