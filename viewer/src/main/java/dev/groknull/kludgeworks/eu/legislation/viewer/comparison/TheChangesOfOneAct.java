// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextOccurrences;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * The feature "The changes of one act": one function for each of its rules. A rule reads facts and
 * gives one new fact. It does not call another rule.
 */
public final class TheChangesOfOneAct {

  private TheChangesOfOneAct() {}

  /**
   * Limited to one act, the changes are those between the first text that includes the act and the
   * text just before it. So the changes of two acts that one text first includes are the same
   * changes.
   *
   * @param timeline the versions of the act in the language
   * @param included the acts that each text includes
   * @param from the version of the earlier text
   * @param to the version of the later text
   * @param act the one act
   * @return the two texts of the changes of the act. Empty if no version after {@code from}, up to
   *     {@code to}, includes the act.
   */
  @BusinessRule(group = RuleGroup.THE_CHANGES_OF_ONE_ACT, name = "one-act-is-one-step")
  public static Optional<Inclusion> oneActIsOneStep(
      Timeline timeline, Inclusions included, Version from, Version to, ActIdentifier act) {
    return timeline.versions().stream()
        .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
        .filter(v -> included.includes(v, act))
        .findFirst()
        .map(first -> new Inclusion(timeline.predecessor(first).orElse(first), first));
  }

  /**
   * One of two or more acts can make a difference while the data does not say which act. Then the
   * page of each of these acts lists the difference apart from its own changes. It also names the
   * other acts.
   *
   * @param act the act of the page
   * @param attributed the differences, each with who made it
   * @return the differences that the act or another act made, with the other acts
   */
  @BusinessRule(group = RuleGroup.THE_CHANGES_OF_ONE_ACT, name = "this-act-or-another")
  public static Alternatives thisActOrAnother(ActIdentifier act, List<Attributed> attributed) {
    var differences = new ArrayList<Difference>();
    var others = new LinkedHashSet<ActIdentifier>();
    for (var each : attributed) {
      var candidates = each.attribution().candidates();
      if (candidates.contains(act)) {
        differences.add(each.difference());
        candidates.stream().filter(other -> !other.equals(act)).forEach(others::add);
      }
    }
    return new Alternatives(differences, List.copyOf(others));
  }

  /**
   * Cellar can give a chapter of an annex as the place of a change, while the text keeps the
   * chapter flat. Its points and its attachments then stand beside it, not in it ({@link
   * TextOccurrences#holds}), and the chapter has no difference of its own. The changes of the act
   * there are the differences that the act made in the points and the attachments that the chapter
   * holds.
   *
   * @param attributed the differences, each with who made it
   * @param places the eIds of the occurrences of the place
   * @param act the act
   * @return the differences in the places that the act made
   */
  @BusinessRule(group = RuleGroup.THE_CHANGES_OF_ONE_ACT, name = "chapter-the-annex-keeps-flat")
  public static List<Difference> chapterTheAnnexKeepsFlat(
      List<Attributed> attributed, List<String> places, ActIdentifier act) {
    return attributed.stream()
        .filter(each ->
            places.stream()
                .anyMatch(place -> TextOccurrences.holds(place, each.difference().eId())))
        .filter(each -> each.attribution().acts().contains(act))
        .map(Attributed::difference)
        .toList();
  }

  /**
   * The differences at a place are the differences of its occurrences and of the occurrences in
   * them. If there are none, it is the one difference of the nearest occurrence that holds one of
   * the occurrences ({@link Difference#holds}). For example, a chapter that an act inserted whole
   * is one difference, and an article in the chapter has no difference of its own, although its eId
   * does not start with the chapter's (art_22b in cpt_IIIa of 767/2008).
   *
   * @param differences the differences of the comparison, in order
   * @param eIds the eIds of the occurrences of the place
   * @return the differences at the place, in the order of the comparison. Empty if the occurrences
   *     are the same in both texts.
   */
  @BusinessRule(group = RuleGroup.THE_CHANGES_OF_ONE_ACT, name = "differences-at-a-place")
  public static List<Difference> differencesAtAPlace(
      List<Difference> differences, List<String> eIds) {
    var within =
        differences.stream()
            .filter(each ->
                eIds.stream()
                    .anyMatch(eId -> each.eId().equals(eId) || each.eId().startsWith(eId + "/")))
            .toList();
    if (!within.isEmpty()) {
      return within;
    }
    return differences.stream()
        .filter(each -> eIds.stream().anyMatch(each::holds))
        .max(Comparator.comparingInt(each -> each.eId().length()))
        .map(List::of)
        .orElseGet(List::of);
  }
}
