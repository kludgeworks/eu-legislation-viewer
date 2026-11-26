// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The feature "Who made a change": one function for each of its rules. A rule reads facts and gives
 * one new fact: the tags of an occurrence, Cellar's amendments of it, the acts that each text
 * includes, or a fact that an earlier rule gave. It does not call another rule.
 *
 * <ul>
 *   <li>{@link Attribution#of} applies the rules that tell who made a difference.
 *   <li>The differ applies the rules of the tags when it reads the two texts.
 * </ul>
 */
public final class WhoMadeAChange {

  private WhoMadeAChange() {}

  /**
   * A sidenote names only the acts between the two texts: the acts that the texts after the earlier
   * text include, up to the later text. It does not name an act that the earlier text includes
   * already. There is one exception: a part of that act applies between the two texts ({@link
   * #actNamedForAPart}).
   *
   * @param betweenTheTexts whether the act is between the two texts
   * @param namedForAPart whether the act is named for a part that applies between the two texts
   * @return whether a sidenote can name the act
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "only-acts-between-the-texts")
  public static boolean onlyActsBetweenTheTexts(boolean betweenTheTexts, boolean namedForAPart) {
    return betweenTheTexts || namedForAPart;
  }

  /**
   * An occurrence's tags are its own, each once and in order. An occurrence without tags takes
   * those of the nearest occurrence around it that has some. The viewer finds the tags in this way
   * for the occurrence of a difference, and for a neighbour of a difference.
   *
   * @param itsOwn the tags on the own content of the occurrence
   * @param ofTheOccurrencesAround the tags of each occurrence around it, the nearest first
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "tags-of-an-occurrence", revision = 2)
  public static List<Tag> tagsOfAnOccurrence(
      List<Tag> itsOwn, List<List<Tag>> ofTheOccurrencesAround) {
    if (!itsOwn.isEmpty()) {
      return itsOwn;
    }
    return ofTheOccurrencesAround.stream()
        .filter(tags -> !tags.isEmpty())
        .findFirst()
        .orElseGet(List::of);
  }

  /**
   * The later text does not have a deleted occurrence, so the occurrence has no tags of its own
   * that tell which act deleted it. Other tags can tell it:
   *
   * <ul>
   *   <li>If the later text keeps the occurrence as a kept deletion, the act of that deletion
   *       deleted it.
   *   <li>If not, an act that replaced the whole occurrence around it deleted it with the rest: the
   *       nearest such occurrence.
   * </ul>
   *
   * <p>If neither applies, no tag names the act.
   *
   * @param ofTheKeptDeletion the tags of the kept deletion. Null if the later text does not keep
   *     the occurrence.
   * @param ofReplacementsAround the tags that say that an act replaced an occurrence around it
   *     whole, for each such occurrence, the nearest first
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "who-deleted-an-occurrence")
  public static List<Tag> whoDeletedAnOccurrence(
      @Nullable List<Tag> ofTheKeptDeletion, List<List<Tag>> ofReplacementsAround) {
    if (ofTheKeptDeletion != null) {
      return ofTheKeptDeletion;
    }
    return ofReplacementsAround.stream()
        .filter(tags -> !tags.isEmpty())
        .findFirst()
        .orElseGet(List::of);
  }

  /**
   * For each text, the tags decide which acts the sidenote names. Cellar names the acts of a text
   * only if no tag names an act of that text.
   *
   * @param byTags the acts that the tags name, each with its modification, in order
   * @param byCellar the acts that Cellar's amendments name, each with its modification, in order
   * @param textOf the first text that has each act, for each act that a text between the two texts
   *     has
   * @return the acts that the sidenote names: those of the tags, then those that only Cellar names
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "tags-else-cellar")
  public static Map<ActIdentifier, Modification> tagsElseCellar(
      Map<ActIdentifier, Modification> byTags,
      Map<ActIdentifier, Modification> byCellar,
      Map<ActIdentifier, Version> textOf) {
    var tagged = new LinkedHashSet<Version>();
    byTags.keySet().stream().map(textOf::get).filter(text -> text != null).forEach(tagged::add);
    var named = new LinkedHashMap<>(byTags);
    byCellar.forEach((act, modification) -> {
      var text = textOf.get(act);
      if (!named.containsKey(act) && (text == null || !tagged.contains(text))) {
        named.put(act, modification);
      }
    });
    return named;
  }

  /**
   * Acts whose tags say that they replaced the same occurrence changed it together. The occurrence
   * can be the occurrence itself or an occurrence around it. If one act replaced the occurrence
   * after the other, only its own tag would be there.
   *
   * @param tags the tags of the occurrence
   * @param between the acts between the two texts
   * @return the acts that changed the occurrence together. Each set has two or more acts.
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "acts-changed-together")
  public static List<Set<ActIdentifier>> actsChangedTogether(
      List<Tag> tags, Collection<ActIdentifier> between) {
    var byOccurrence = new LinkedHashMap<Optional<String>, Set<ActIdentifier>>();
    for (var tag : tags) {
      var each = new ActIdentifier(tag.act());
      if (tag.modification() == Modification.REPLACED && between.contains(each)) {
        byOccurrence
            .computeIfAbsent(Optional.ofNullable(tag.in()), in -> new LinkedHashSet<>())
            .add(each);
      }
    }
    return byOccurrence.values().stream().filter(acts -> acts.size() > 1).toList();
  }

  /**
   * If no tag and no amendment in Cellar names an act for a difference, the sidenote does not guess
   * the act. It names the acts of which one made the change if two or more acts that are not
   * corrigenda are between the two texts, and one text is the first text that includes each of
   * them.
   *
   * @param included the acts that each text includes
   * @param from the version of the earlier text
   * @param to the version of the later text
   * @return the acts of which one made the change, in the order of the text that first includes
   *     them. Empty if the conditions are not true: the sidenote then names no act, and names the
   *     first text that has the change ({@link #firstTextWithTheChange}).
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "no-act-named")
  public static List<ActIdentifier> noActNamed(Inclusions included, Version from, Version to) {
    var candidates =
        included.between(from, to).stream().filter(each -> !each.corrigendum()).toList();
    var texts =
        candidates.stream()
            .map(each -> Applicability.firstIncluding(included, from, to, each))
            .flatMap(Optional::stream)
            .distinct()
            .toList();
    if (candidates.size() < 2 || texts.size() != 1) {
      return List.of();
    }
    return included.of(texts.getFirst()).stream().filter(candidates::contains).toList();
  }

  /**
   * If the sidenote names no act for a difference ({@link #noActNamed}), it names the first text
   * that has the change. That text is the later text of the first pair of neighbouring texts, after
   * the earlier text and up to the later text, whose comparison has a difference at the occurrence.
   * A pair whose texts are not both built is passed over.
   *
   * @param timeline the versions of the act in the language
   * @param from the version of the earlier text
   * @param to the version of the later text
   * @param eIds the eIds of the occurrences of the changes
   * @param differing for a version, the eIds of the differences between the text of the version
   *     before it and its own text, or empty if the two texts are not both built
   * @return the version whose text first has each change, by the eId of its occurrence. A change
   *     that no pair has is not in the result.
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "first-text-with-the-change")
  public static Map<String, Version> firstTextWithTheChange(
      Timeline timeline,
      Version from,
      Version to,
      Set<String> eIds,
      Function<Version, Optional<Set<String>>> differing) {
    var found = new HashMap<String, Version>();
    if (eIds.isEmpty()) {
      return found;
    }
    // The comparison itself is the only pair: each of its changes appears in the later text.
    if (timeline.predecessor(to).filter(from::equals).isPresent()) {
      eIds.forEach(eId -> found.put(eId, to));
      return found;
    }
    for (var version : timeline.versions()) {
      if (!version.date().isAfter(from.date()) || version.date().isAfter(to.date())) {
        continue;
      }
      differing.apply(version).stream()
          .flatMap(Set::stream)
          .filter(eIds::contains)
          .forEach(eId -> found.putIfAbsent(eId, version));
      if (found.size() == eIds.size()) {
        break;
      }
    }
    return found;
  }

  /**
   * The amendments of an occurrence in Cellar include those of an occurrence around it. If the
   * comparison shows the whole occurrence as inserted or deleted, they also include those of the
   * occurrences in it. A point numbered under a point, which the text keeps beside it, counts as in
   * it ({@link Amended#of}).
   *
   * @param amended Cellar's amendments that count, by occurrence
   * @param difference the difference of the occurrence
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "amendments-around-and-inside")
  public static List<Amendment> amendmentsAroundAndInside(Amended amended, Difference difference) {
    return amended.of(difference.eId(), difference.kind() != Kind.CHANGED);
  }

  /**
   * A difference that only corrigenda made is a correction, not an amendment. A difference that an
   * amending act made, alone or together with a corrigendum, is not a correction. Nor is a
   * difference that no known act made.
   *
   * @param acts the acts that made the difference
   * @return whether the difference is a correction
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "corrigendum-corrects")
  public static boolean corrigendumCorrects(List<ActIdentifier> acts) {
    return !acts.isEmpty() && acts.stream().allMatch(ActIdentifier::corrigendum);
  }

  /**
   * The acts are in steps, one step for each text that first includes them, oldest first. Each step
   * has its acts in the order that its text lists them. An act that is named for a part and that
   * its text does not list comes after them.
   *
   * @param textsBetween the versions after the earlier text, up to the later text, in order
   * @param textOf the text of the step of each act
   * @param included the acts that each text includes
   * @param forPart the acts that are named for a part, in order
   * @return the steps: each text that has acts, with its acts
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "one-step-for-each-text")
  public static List<Map.Entry<Version, List<ActIdentifier>>> oneStepForEachText(
      List<Version> textsBetween,
      Map<ActIdentifier, Version> textOf,
      Inclusions included,
      List<ActIdentifier> forPart) {
    var steps = new ArrayList<Map.Entry<Version, List<ActIdentifier>>>();
    for (var version : textsBetween) {
      var step = new ArrayList<ActIdentifier>();
      included.of(version).stream()
          .filter(each -> version.equals(textOf.get(each)))
          .forEach(step::add);
      forPart.stream()
          .filter(each -> version.equals(textOf.get(each)) && !step.contains(each))
          .forEach(step::add);
      if (!step.isEmpty()) {
        steps.add(Map.entry(version, List.copyOf(step)));
      }
    }
    return steps;
  }

  /**
   * The act that the tags name was the last act to change the occurrence. So its step is in no text
   * before the texts of the acts that only Cellar names, even if an earlier text includes it. If it
   * changed the occurrence together with other acts, its step is in the first such text that has
   * all of them.
   *
   * @param act the act that the tags name
   * @param together the acts that changed the occurrence together with it, with the act itself
   * @param lastTextOfCellarsActs the date of the last text of the acts that only Cellar names. Null
   *     if Cellar names no other act.
   * @param textsBetween the versions after the earlier text, up to the later text, in order
   * @param included the acts that each text includes
   * @return the text of the step of the act, or empty if no text between the two texts has it
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "tagged-act-was-last")
  public static Optional<Version> taggedActWasLast(
      ActIdentifier act,
      Set<ActIdentifier> together,
      @Nullable LocalDate lastTextOfCellarsActs,
      List<Version> textsBetween,
      Inclusions included) {
    var after =
        textsBetween.stream()
            .filter(v -> lastTextOfCellarsActs == null || !v.date().isBefore(lastTextOfCellarsActs))
            .toList();
    return after.stream()
        .filter(v -> included.of(v).containsAll(together))
        .findFirst()
        .or(() -> after.stream().filter(v -> included.includes(v, act)).findFirst())
        .or(() -> textsBetween.stream().filter(v -> included.includes(v, act)).findFirst());
  }

  /**
   * A sidenote names each act with what the act did to the occurrence in its step. The first item
   * that applies to the step gives its modification:
   *
   * <ol>
   *   <li>corrected: the step of a corrigendum
   *   <li>inserted: the first step of an inserted occurrence
   *   <li>deleted: the last step of a deleted occurrence
   *   <li>replaced or renumbered: another step, as the act's tag says
   *   <li>amended: another step. The act inserted words in an occurrence that is still there, or
   *       deleted words from it.
   * </ol>
   *
   * @param kind the kind of the difference
   * @param first whether the step is the first step
   * @param last whether the step is the last step
   * @param act the first act of the step
   * @param tagged the modification that the act's tag, or Cellar, gives
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "modification-of-each-step")
  public static Modification modificationOfEachStep(
      Kind kind, boolean first, boolean last, ActIdentifier act, Modification tagged) {
    if (act.corrigendum()) {
      return Modification.CORRECTED;
    }
    if (first && kind == Kind.INSERTED) {
      return Modification.INSERTED;
    }
    if (last && kind == Kind.DELETED) {
      return Modification.DELETED;
    }
    return tagged == Modification.REPLACED || tagged == Modification.RENUMBERED
        ? tagged
        : Modification.AMENDED;
  }

  /**
   * If the data names no act for a difference, the modification tells what happened to the
   * occurrence: inserted, deleted, or amended for an occurrence in both texts with different words.
   *
   * @param kind the kind of the difference
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "modification-without-an-act")
  public static Modification modificationWithoutAnAct(Kind kind) {
    return switch (kind) {
      case INSERTED -> Modification.INSERTED;
      case DELETED -> Modification.DELETED;
      case CHANGED -> Modification.AMENDED;
    };
  }

  /**
   * An earlier text can include an act already. The act can change the text again when a later part
   * of the act starts to apply. The sidenote names the act for the change if the occurrence has the
   * tag of the act, and one of the dates of the act falls after the earlier day, up to and on the
   * later day. For example, 2024/1619 on 2013/36 from 17 January 2025 to 11 January 2026: the act
   * is included from July 2024, and a part of it applies from 11 January 2026.
   *
   * <p>A tag can also be new only because the earlier text has no tags. The date tells these apart.
   *
   * @param datesBetweenTheTexts the days of the dates of the act after the earlier day, up to and
   *     on the later day
   * @param textsBetween the versions after the earlier text, up to the later text, in order
   * @return the text that takes the part in: the first text from the first of those days. Empty if
   *     the act has no such date.
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "act-named-for-a-part")
  public static Optional<Version> actNamedForAPart(
      Set<LocalDate> datesBetweenTheTexts, List<Version> textsBetween) {
    return datesBetweenTheTexts.stream()
        .min(Comparator.naturalOrder())
        .flatMap(day -> textsBetween.stream().filter(v -> !v.date().isBefore(day)).findFirst());
  }

  /**
   * If the text has no tag for an act, Cellar gives the act's amendment of the occurrence a role.
   * The role tells what the act did: a replacement replaced, an addition inserted, a deletion or a
   * repeal deleted, an amendment amended.
   *
   * <p>A role that changes when the words apply, not the words (an extension, a suspension, a
   * deferral), or that Cellar does not type, gives no modification of the words. Such an amendment
   * never reaches a sidenote: only the amendments that change the words count.
   *
   * @param role the role of the amendment
   * @throws IllegalArgumentException for a role that does not change the words
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "modification-from-cellar")
  public static Modification modificationFromCellar(Amendment.Role role) {
    return switch (role) {
      case REPLACEMENT -> Modification.REPLACED;
      case DELETION, REPEAL -> Modification.DELETED;
      case AMENDMENT -> Modification.AMENDED;
      case ADDITION -> Modification.INSERTED;
      case EXTENSION, SUSPENSION, DEFERRAL, OTHER ->
          throw new IllegalArgumentException(
              "A "
                  + role
                  + " does not change the words, so it has no modification: only an amendment that"
                  + " changes the words reaches a sidenote");
    };
  }

  /**
   * An act can change an occurrence only while the law has the occurrence: up to the act that
   * deleted it, as Cellar records it. The law has it up to the first text that includes one of
   * Cellar's deletions or repeals of it. A consolidated text can still print it after that. An act
   * that a later text first includes changed the occurrence around it, or what took its place, not
   * the occurrence. If nothing takes it out, each act can have changed it.
   *
   * @param firstIncluded the date of the first text between the two texts that includes the act.
   *     Null if none does.
   * @param removalsFirstIncluded the same date for each act that took the occurrence out of the law
   * @return whether the act can have changed the occurrence
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "act-changes-only-what-the-law-has")
  public static boolean actChangesOnlyWhatTheLawHas(
      @Nullable LocalDate firstIncluded, List<LocalDate> removalsFirstIncluded) {
    var removed = removalsFirstIncluded.stream().min(Comparator.naturalOrder());
    return removed.isEmpty() || firstIncluded == null || !firstIncluded.isAfter(removed.get());
  }
}
