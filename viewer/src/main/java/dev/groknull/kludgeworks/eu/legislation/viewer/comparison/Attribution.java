// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextOccurrences;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Who made a difference: the acts that made it, in their steps, or the acts of which one made it
 * where it is not known which. The sidenote of the difference says it in words.
 *
 * <p>{@link #of} finds the acts and puts them in steps. The acts come from two sources: the tags on
 * the occurrence in the later text, and Cellar's amendments of the occurrence. Only acts between
 * the two texts count, and an act of which a part applies between them.
 *
 * @param acts the acts that made the difference, in the order that the texts include them. The
 *     difference counts in the changes of each of these acts. Empty where no act is known.
 * @param candidates the acts of which one made the difference, where it is not known which act.
 *     Empty where the act is known, or where nothing names an act.
 * @param steps the acts in their steps, oldest first. Empty where no act is known, or where the
 *     attribution names its acts without their steps.
 */
@ValueObject
public record Attribution(
    List<ActIdentifier> acts, List<ActIdentifier> candidates, List<Step> steps) {

  /** No act is known to have made the difference, and no act is named as one that may have. */
  public static final Attribution NONE = new Attribution(List.of(), List.of(), List.of());

  public Attribution {
    acts = List.copyOf(acts);
    candidates = List.copyOf(candidates);
    steps = List.copyOf(steps);
  }

  /**
   * One step of the changes of an occurrence: the acts that the first text to include them
   * includes, and what the first of them did.
   *
   * @param version the version whose text first includes the acts of the step
   * @param acts the acts, in the order that the version lists them. Two or more acts in one step
   *     changed the occurrence together, with one wording.
   * @param modification what the acts of the step did to the occurrence
   * @param forPart the acts of the step that an earlier text included already. A part of each
   *     applies in the step.
   */
  @ValueObject
  public record Step(
      Version version,
      List<ActIdentifier> acts,
      Modification modification,
      Set<ActIdentifier> forPart) {
    public Step {
      acts = List.copyOf(acts);
      forPart = Set.copyOf(forPart);
    }
  }

  /** Returns the attribution of a difference that {@code acts} made, without their steps. */
  public static Attribution madeBy(List<ActIdentifier> acts) {
    return new Attribution(acts, List.of(), List.of());
  }

  /** Returns the attribution of a difference that the acts of {@code steps} made. */
  public static Attribution inSteps(List<Step> steps) {
    return new Attribution(
        steps.stream().flatMap(step -> step.acts().stream()).toList(), List.of(), steps);
  }

  /**
   * Returns the attribution of a difference that one of {@code candidates} made, not known which.
   */
  public static Attribution oneOf(List<ActIdentifier> candidates) {
    return new Attribution(List.of(), candidates, List.of());
  }

  /**
   * Returns Cellar's amendments of the occurrence of {@code difference}: those at the occurrence,
   * and those at an occurrence around it. For an occurrence that was inserted or deleted whole,
   * also those at the occurrences in it. A point numbered under a point, which the text keeps
   * beside it, counts as in it ({@link Amended#of}).
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "amendments-around-and-inside")
  public static List<Amendment> amendmentsOf(Amended amended, Difference difference) {
    return amended.of(difference.eId(), difference.kind() != Difference.Kind.CHANGED);
  }

  /**
   * Returns Cellar's amendments that take the occurrence of {@code difference} out of the text: a
   * deletion or a repeal at the occurrence, or at an occurrence around it. An amendment at an
   * occurrence in it does not take it out. Empty for a difference that is not deleted.
   */
  public static List<Amendment> removalsOf(Amended amended, Difference difference) {
    if (difference.kind() != Difference.Kind.DELETED) {
      return List.of();
    }
    return amended.of(difference.eId(), false).stream()
        .filter(each -> each.role().removes())
        .toList();
  }

  /**
   * Returns which acts can have changed the occurrence. An act changes an occurrence only while the
   * law has it. If {@code removals}, Cellar's deletions and repeals, take the occurrence out of the
   * law, the law has it up to the first version after {@code from} that includes one of them. A
   * consolidated text can still print it after that. An act that a later version first includes
   * changed the occurrence around it, or what took its place, not the occurrence. If nothing takes
   * it out, each act can have changed it.
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "act-changes-only-what-the-law-has")
  private static Predicate<ActIdentifier> whileTheLawHasIt(
      Inclusions included, Version from, Version to, List<Amendment> removals) {
    var removed =
        removals.stream()
            .map(each -> Applicability.firstIncluding(included, from, to, each.act()))
            .flatMap(Optional::stream)
            .map(Version::date)
            .min(Comparator.naturalOrder());
    if (removed.isEmpty()) {
      return act -> true;
    }
    var last = removed.get();
    return act ->
        Applicability.firstIncluding(included, from, to, act)
            .map(version -> !version.date().isAfter(last))
            .orElse(true);
  }

  /**
   * Returns the attribution of a difference for which nothing names an act. If two or more acts
   * that are not corrigenda are between the two versions, and one version first includes them all,
   * one of them made it: they are the candidates, in the order of that version. If not, no act is
   * named.
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "no-act-named")
  public static Attribution unnamed(Inclusions included, Version from, Version to) {
    var candidates =
        included.between(from, to).stream().filter(each -> !each.corrigendum()).toList();
    var versions =
        candidates.stream()
            .map(each -> Applicability.firstIncluding(included, from, to, each))
            .flatMap(Optional::stream)
            .distinct()
            .toList();
    if (candidates.size() < 2 || versions.size() != 1) {
      return NONE;
    }
    return oneOf(included.of(versions.getFirst()).stream().filter(candidates::contains).toList());
  }

  /**
   * Returns who made {@code difference}, in the comparison of the version {@code from} of {@code
   * act} with the version {@code to}. Where neither the tags nor Cellar name an act between the two
   * texts, it does not guess: it returns the acts of which one made it, if there are such acts
   * ({@link #unnamed}), or else {@link #NONE}.
   *
   * <ul>
   *   <li>The acts are those between the two texts. An act that the tags name counts. So does an
   *       act that only Cellar names, where the text has no tag of it. Where the tags name an act
   *       of a version, Cellar's other acts of that version are left out.
   *   <li>An act that an earlier text included already counts if its tag is on the occurrence, and
   *       a part of it applies after {@code from}, up to {@code to}.
   *   <li>There is one step for each version, in the order that the texts include the acts. The act
   *       that the tags name comes after the acts that only Cellar names. Acts that replaced the
   *       occurrence together are in the first version that has them all.
   * </ul>
   *
   * @param included the acts that each version includes: Cellar's lists, and the acts that the
   *     built texts tag first
   * @param amendments Cellar's amendments of the occurrence, by the acts between the two versions
   * @param removals Cellar's amendments that take the occurrence out of the text ({@link
   *     #removalsOf}). No act after them changed it ({@link #whileTheLawHasIt}).
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "only-acts-between-the-texts")
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "tags-else-cellar")
  public static Attribution of(
      Act act,
      Inclusions included,
      Version from,
      Version to,
      Difference difference,
      List<Amendment> amendments,
      List<Amendment> removals) {
    var between = included.between(from, to);
    var stands = whileTheLawHasIt(included, from, to, removals);
    var parts = parts(act, included, from, to, difference, between);
    var tagged =
        acts(
            difference.tags(),
            each -> (between.contains(each) || parts.containsKey(each)) && stands.test(each));
    var taggedVersions =
        tagged.keySet().stream()
            .map(
                each ->
                    Optional.ofNullable(parts.get(each))
                        .or(() -> Applicability.firstIncluding(included, from, to, each)))
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
    var made = new LinkedHashMap<>(tagged);
    for (var amendment : amendments) {
      var each = amendment.act();
      // Leave out the act if the tags name an act of the same version. The tags take priority.
      if (between.contains(each)
          && stands.test(each)
          && !made.containsKey(each)
          && Applicability.firstIncluding(included, from, to, each)
              .filter(taggedVersions::contains)
              .isEmpty()) {
        made.put(each, Modification.of(amendment.role()));
      }
    }
    if (made.isEmpty()) {
      return unnamed(included, from, to);
    }
    var placed =
        place(
            included,
            from,
            to,
            made.keySet(),
            tagged.keySet(),
            parts,
            together(difference, between));
    var steps = new ArrayList<Step>();
    for (var i = 0; i < placed.size(); i++) {
      var version = placed.get(i).getKey();
      var acts = placed.get(i).getValue();
      var first = acts.getFirst();
      steps.add(
          new Step(
              version,
              acts,
              Modification.ofStep(
                  difference.kind(),
                  i == 0,
                  i == placed.size() - 1,
                  first,
                  Objects.requireNonNull(made.get(first))),
              acts.stream().filter(parts::containsKey).collect(Collectors.toSet())));
    }
    return inSteps(steps);
  }

  /**
   * Returns whether the difference is a correction: only corrigenda made it. A difference that an
   * amending act made together with a corrigendum is not a correction. Nor is a difference that no
   * known act made.
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "corrigendum-corrects")
  public boolean corrected() {
    return !acts.isEmpty() && acts.stream().allMatch(ActIdentifier::corrigendum);
  }

  /**
   * Returns the differences of {@code attributed} in the places {@code places} that {@code act}
   * made, for a place that has no difference of its own. Cellar can name as the place of a change a
   * chapter of an annex that the text keeps flat: its points and its attachments stand beside it,
   * not in it ({@link TextOccurrences#holds}). The changes of the act are then the differences in
   * the points and the attachments that the chapter holds, where the act made them.
   */
  @BusinessRule(group = RuleGroup.THE_CHANGES_OF_ONE_ACT, name = "chapter-the-annex-keeps-flat")
  public static List<Difference> madeIn(
      List<Attributed> attributed, List<String> places, ActIdentifier act) {
    return attributed.stream()
        .filter(
            each ->
                places.stream()
                    .anyMatch(place -> TextOccurrences.holds(place, each.difference().eId())))
        .filter(each -> each.attribution().acts().contains(act))
        .map(Attributed::difference)
        .toList();
  }

  /** Returns whether no act is named: neither an act that made it, nor acts of which one did. */
  public boolean unattributed() {
    return acts.isEmpty() && candidates.isEmpty();
  }

  /**
   * Returns the acts that changed the occurrence of {@code difference} in a part that applies
   * between the two versions, by the version that takes the part in. Such an act is not between the
   * two versions: an earlier version included it already. One of its dates falls after {@code
   * from}, up to {@code to}. For example, 2024/1619 on 2013/36 from 17 January 2025 to 11 January
   * 2026: the act is included from July 2024, and a part of it applies from 11 January 2026.
   *
   * <p>A tag can also be new only because the earlier text has no tags. The date tells these apart.
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "act-named-for-a-part")
  private static Map<ActIdentifier, Version> parts(
      Act act,
      Inclusions included,
      Version from,
      Version to,
      Difference difference,
      Collection<ActIdentifier> between) {
    var parts = new LinkedHashMap<ActIdentifier, Version>();
    for (var tag : difference.tags()) {
      var each = new ActIdentifier(tag.act());
      if (between.contains(each) || parts.containsKey(each)) {
        continue;
      }
      Applicability.daysBetween(act, each, from, to).stream()
          .min(Comparator.naturalOrder())
          .flatMap(
              day ->
                  included.timeline().versions().stream()
                      .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
                      .filter(v -> !v.date().isBefore(day))
                      .findFirst())
          .ifPresent(version -> parts.put(each, version));
    }
    return parts;
  }

  /**
   * Returns the acts that {@code tags} name and that {@code kept} accepts, in order. Each act comes
   * with the modification of its first tag.
   */
  private static Map<ActIdentifier, Modification> acts(
      List<Tag> tags, Predicate<ActIdentifier> kept) {
    var acts = new LinkedHashMap<ActIdentifier, Modification>();
    for (var tag : tags) {
      var act = new ActIdentifier(tag.act());
      if (kept.test(act)) {
        acts.putIfAbsent(act, tag.modification());
      }
    }
    return acts;
  }

  /**
   * Returns the acts in {@code acts} by the version after {@code from}, up to {@code to}, that
   * first integrates them, in the order of the versions. Each version holds its acts in the order
   * that it lists them.
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "one-step-for-each-text")
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "tagged-act-was-last")
  private static List<Map.Entry<Version, List<ActIdentifier>>> place(
      Inclusions included,
      Version from,
      Version to,
      Collection<ActIdentifier> acts,
      Collection<ActIdentifier> tagged,
      Map<ActIdentifier, Version> parts,
      List<Set<ActIdentifier>> together) {
    var between =
        included.timeline().versions().stream()
            .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
            .toList();
    // The version of each act is the first version that has it. An act that applies from more than
    // one day is
    // in several versions.
    var at = new HashMap<ActIdentifier, Version>();
    for (var each : acts) {
      if (!tagged.contains(each)) {
        between.stream()
            .filter(v -> included.includes(v, each))
            .findFirst()
            .ifPresent(version -> at.put(each, version));
      }
    }
    // An act that the tags name was the last act to change the occurrence. Thus it is in no
    // version before the versions of the other acts. If it changed the occurrence together with
    // other acts, it is in the first version that has all of them.
    var floor = at.values().stream().map(Version::date).max(Comparator.naturalOrder()).orElse(null);
    for (var each : tagged) {
      var part = parts.get(each);
      if (part != null) {
        at.put(each, part);
        continue;
      }
      var with =
          together.stream()
              .filter(group -> group.contains(each))
              .findFirst()
              .orElseGet(() -> Set.of(each));
      var after = between.stream().filter(v -> floor == null || !v.date().isBefore(floor)).toList();
      after.stream()
          .filter(v -> included.of(v).containsAll(with))
          .findFirst()
          .or(() -> after.stream().filter(v -> included.includes(v, each)).findFirst())
          .or(() -> between.stream().filter(v -> included.includes(v, each)).findFirst())
          .ifPresent(version -> at.put(each, version));
    }
    var placed = new ArrayList<Map.Entry<Version, List<ActIdentifier>>>();
    for (var version : between) {
      // Keep the order that the version gives the acts.
      var step = new ArrayList<ActIdentifier>();
      included.of(version).stream()
          .filter(each -> acts.contains(each) && version.equals(at.get(each)))
          .forEach(step::add);
      parts.entrySet().stream()
          .filter(
              part ->
                  acts.contains(part.getKey())
                      && version.equals(part.getValue())
                      && !step.contains(part.getKey()))
          .map(Map.Entry::getKey)
          .forEach(step::add);
      if (!step.isEmpty()) {
        placed.add(Map.entry(version, List.copyOf(step)));
      }
    }
    return placed;
  }

  /**
   * Returns the acts between the two versions that replaced the occurrence together, by its tags.
   * Two acts that replaced the same occurrence as a whole did so at the same time. The occurrence
   * can be the occurrence itself or the occurrence it is in. If one act replaced the occurrence
   * after the other, only its own tag would be there. Each set has two or more acts.
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "acts-changed-together")
  private static List<Set<ActIdentifier>> together(
      Difference difference, Collection<ActIdentifier> between) {
    var byOccurrence = new LinkedHashMap<Optional<String>, Set<ActIdentifier>>();
    for (var tag : difference.tags()) {
      var each = new ActIdentifier(tag.act());
      if (tag.modification() == Modification.REPLACED && between.contains(each)) {
        byOccurrence
            .computeIfAbsent(Optional.ofNullable(tag.in()), in -> new LinkedHashSet<>())
            .add(each);
      }
    }
    return byOccurrence.values().stream().filter(acts -> acts.size() > 1).toList();
  }
}
