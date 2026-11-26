// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Who made a difference: the acts that made it, in their steps, or the acts of which one made it
 * where it is not known which. The sidenote of the difference says it in words.
 *
 * <p>{@link #of} finds the acts and puts them in steps: it applies the rules of {@link
 * WhoMadeAChange}. The acts come from two sources: the tags on the occurrence in the later text,
 * and Cellar's amendments of the occurrence.
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
   * Returns who made {@code difference}, in the comparison of the version {@code from} of {@code
   * act} with the version {@code to}. It applies the rules of {@link WhoMadeAChange} in order:
   *
   * <ol>
   *   <li>The acts that are named for a part that applies between the two texts.
   *   <li>The acts that the tags name, then the acts that Cellar names. Only acts between the two
   *       texts count, and only acts that changed the occurrence while the law had it.
   *   <li>The tags, else Cellar, name the acts of each text.
   *   <li>If no act is named: the acts of which one made the difference, or else {@link #NONE}.
   *   <li>The text of the step of each act, the steps, and the modification of each step.
   * </ol>
   *
   * @param included the acts that each version includes: Cellar's lists, and the acts that the
   *     built texts tag first
   * @param amendments Cellar's amendments of the occurrence, by the acts between the two versions
   *     ({@link WhoMadeAChange#amendmentsAroundAndInside})
   * @param removals Cellar's amendments that take the occurrence out of the text ({@link
   *     #removalsOf})
   */
  public static Attribution of(
      Act act,
      Inclusions included,
      Version from,
      Version to,
      Difference difference,
      List<Amendment> amendments,
      List<Amendment> removals) {
    var between = included.between(from, to);
    var texts =
        included.timeline().versions().stream()
            .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
            .toList();
    Function<ActIdentifier, Optional<Version>> firstWith =
        each -> texts.stream().filter(v -> included.includes(v, each)).findFirst();
    var removed =
        removals.stream()
            .map(each -> firstWith.apply(each.act()))
            .flatMap(Optional::stream)
            .map(Version::date)
            .toList();
    Predicate<ActIdentifier> stands =
        each ->
            WhoMadeAChange.actChangesOnlyWhatTheLawHas(
                firstWith.apply(each).map(Version::date).orElse(null), removed);

    var parts = new LinkedHashMap<ActIdentifier, Version>();
    for (var tag : difference.tags()) {
      var each = new ActIdentifier(tag.act());
      if (!between.contains(each) && !parts.containsKey(each)) {
        WhoMadeAChange.actNamedForAPart(Applicability.daysBetween(act, each, from, to), texts)
            .ifPresent(version -> parts.put(each, version));
      }
    }

    var byTags = new LinkedHashMap<ActIdentifier, Modification>();
    for (var tag : difference.tags()) {
      var each = new ActIdentifier(tag.act());
      if (WhoMadeAChange.onlyActsBetweenTheTexts(between.contains(each), parts.containsKey(each))
          && stands.test(each)) {
        byTags.putIfAbsent(each, tag.modification());
      }
    }
    var byCellar = new LinkedHashMap<ActIdentifier, Modification>();
    for (var amendment : amendments) {
      var each = amendment.act();
      // An act is named for a part only by its tag.
      if (WhoMadeAChange.onlyActsBetweenTheTexts(between.contains(each), false)
          && stands.test(each)
          && !byCellar.containsKey(each)) {
        byCellar.put(each, WhoMadeAChange.modificationFromCellar(amendment.role()));
      }
    }
    var firstText = new HashMap<ActIdentifier, Version>();
    byCellar.keySet().forEach(each -> firstWith.apply(each).ifPresent(v -> firstText.put(each, v)));
    for (var each : byTags.keySet()) {
      var text = Optional.ofNullable(parts.get(each)).or(() -> firstWith.apply(each));
      if (text.isPresent()) {
        firstText.put(each, text.get());
      } else {
        firstText.remove(each);
      }
    }
    var made = WhoMadeAChange.tagsElseCellar(byTags, byCellar, firstText);
    if (made.isEmpty()) {
      return oneOf(WhoMadeAChange.noActNamed(included, from, to));
    }

    var together = WhoMadeAChange.actsChangedTogether(difference.tags(), between);
    var textOf = new HashMap<ActIdentifier, Version>();
    for (var each : made.keySet()) {
      if (!byTags.containsKey(each)) {
        firstWith.apply(each).ifPresent(version -> textOf.put(each, version));
      }
    }
    var lastOfCellar =
        textOf.values().stream().map(Version::date).max(Comparator.naturalOrder()).orElse(null);
    for (var each : byTags.keySet()) {
      var part = parts.get(each);
      if (part != null) {
        textOf.put(each, part);
        continue;
      }
      var with =
          together.stream()
              .filter(group -> group.contains(each))
              .findFirst()
              .orElseGet(() -> Set.of(each));
      WhoMadeAChange.taggedActWasLast(each, with, lastOfCellar, texts, included)
          .ifPresent(version -> textOf.put(each, version));
    }
    var placed =
        WhoMadeAChange.oneStepForEachText(
            texts, textOf, included, parts.keySet().stream().filter(made::containsKey).toList());
    var steps = new ArrayList<Step>();
    for (var i = 0; i < placed.size(); i++) {
      var version = placed.get(i).getKey();
      var acts = placed.get(i).getValue();
      var first = acts.getFirst();
      steps.add(
          new Step(
              version,
              acts,
              WhoMadeAChange.modificationOfEachStep(
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
   * Returns whether the difference is a correction ({@link WhoMadeAChange#corrigendumCorrects}).
   */
  public boolean corrected() {
    return WhoMadeAChange.corrigendumCorrects(acts);
  }

  /** Returns whether no act is named: neither an act that made it, nor acts of which one did. */
  public boolean unattributed() {
    return acts.isEmpty() && candidates.isEmpty();
  }
}
