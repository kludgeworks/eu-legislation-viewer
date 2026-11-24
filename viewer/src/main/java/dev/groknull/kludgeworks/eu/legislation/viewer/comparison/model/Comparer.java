// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Standing;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Compares the occurrences of two texts.
 *
 * @param before the occurrences of the earlier text
 * @param after the occurrences of the later text
 * @param pairs which occurrence of the later text is which occurrence of the earlier text
 */
record Comparer(Occurrences before, Occurrences after, Pairing pairs) {

  /**
   * Returns the occurrences in the occurrence {@code is} of the later text, compared with the
   * occurrences in the occurrence {@code was} that it pairs with. If both are null, returns the
   * outermost occurrences.
   *
   * <p>The occurrences are in the order of the later text. Each deleted occurrence comes after the
   * later occurrence that pairs with the last paired occurrence before it, or first if there is no
   * such occurrence. An occurrence that moved out of {@code was} is not deleted: it shows where the
   * later text has it.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "one-difference-for-each-occurrence")
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "deleted-text-stands-where-it-was")
  List<ComparedOccurrence> occurrences(@Nullable Occurrence was, @Nullable Occurrence is) {
    var later = in(after, is);
    // The deleted occurrences that stood first, then those that stood after each later
    // occurrence.
    var deleted = new ArrayList<List<ComparedOccurrence>>();
    for (var j = 0; j <= later.size(); j++) {
      deleted.add(new ArrayList<>());
    }
    var place = 0;
    for (var occurrence : in(before, was)) {
      var now = pairs.is(occurrence);
      if (now == null) {
        deleted.get(place).add(deleted(occurrence, is, standing(later, place, is)));
      } else if (later.contains(now)) {
        place = later.indexOf(now) + 1;
      }
    }
    var nodes = new ArrayList<ComparedOccurrence>(deleted.get(0));
    for (var j = 0; j < later.size(); j++) {
      var occurrence = later.get(j);
      var earlier = pairs.was(occurrence);
      nodes.add(earlier == null ? inserted(occurrence) : paired(earlier, occurrence));
      nodes.addAll(deleted.get(j + 1));
    }
    return nodes;
  }

  /**
   * Returns the occurrence {@code occurrence} of only the earlier text. It was in the later
   * occurrence {@code in}, and it {@code stands} there.
   */
  private ComparedOccurrence deleted(
      Occurrence occurrence, @Nullable Occurrence in, @Nullable Standing stands) {
    var difference =
        whole(occurrence, before, Kind.DELETED, in == null ? null : in.eId())
            .earlierTags(Tags.of(occurrence, before))
            .tags(Tags.deleted(occurrence, after, in))
            .stands(stands);
    return ComparedOccurrence.of(occurrence, true, difference, null, List.of());
  }

  /**
   * Returns where a deleted occurrence stands among {@code later}, the occurrences of the later
   * occurrence {@code in}, if it comes at {@code place} among them: after the occurrence before
   * that place. If it comes first, before the first occurrence. If {@code in} has none, after
   * {@code in}. Null if it comes first among the outermost occurrences and the later text has none:
   * at the end of the text.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "deleted-text-stands-where-it-was")
  private static @Nullable Standing standing(
      List<Occurrence> later, int place, @Nullable Occurrence in) {
    if (place > 0) {
      return new Standing(later.get(place - 1).eId(), true);
    }
    if (!later.isEmpty()) {
      return new Standing(later.getFirst().eId(), false);
    }
    return in == null ? null : new Standing(in.eId(), true);
  }

  /** Returns the occurrence {@code occurrence} of only the later text. */
  private ComparedOccurrence inserted(Occurrence occurrence) {
    var difference =
        whole(occurrence, after, Kind.INSERTED, occurrence.parent())
            .tags(Tags.of(occurrence, after));
    return ComparedOccurrence.of(occurrence, false, difference, null, List.of());
  }

  /**
   * Returns the occurrence {@code is} of the later text, compared with the occurrence {@code was}
   * of the earlier text that it pairs with. The occurrence is changed if its own blocks or
   * footnotes differ.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "one-difference-for-each-occurrence")
  private ComparedOccurrence paired(Occurrence was, Occurrence is) {
    var editing = OccurrenceEdits.of(was, is);
    var occurrences = occurrences(was, is);
    if (editing == null) {
      return ComparedOccurrence.of(is, false, null, null, occurrences);
    }
    var difference =
        DifferenceBuilder.builder()
            .eId(is.eId())
            .kind(Kind.CHANGED)
            .occurrence(sibling(is))
            .inserted(editing.blocks().inserted())
            .deleted(editing.blocks().deleted())
            .parent(is.parent())
            .list(is.list())
            .section(is.section())
            .tags(Tags.of(is, after))
            .earlierTags(Tags.of(was, before));
    var node = ComparedOccurrence.of(is, false, difference, editing, occurrences);
    difference.shown(ShownOccurrence.of(node, false));
    return node;
  }

  /**
   * Returns {@code occurrence} of {@code occurrences} as a whole difference, with every word in it
   * shown inserted or deleted, by its {@code kind}. The occurrence is in the occurrence {@code
   * parent} of the later text.
   */
  private static DifferenceBuilder whole(
      Occurrence occurrence, Occurrences occurrences, Kind kind, @Nullable String parent) {
    var edit = kind == Kind.INSERTED ? Edits.INSERTED : Edits.DELETED;
    var edited = Edits.all(occurrence.element(), edit);
    var shown = edited.element();
    var inserted = edited.inserted();
    var deleted = edited.deleted();
    var referred = occurrences.footnotesOf(occurrence.element());
    if (!referred.isEmpty()) {
      var footnotes = Edits.all(Differ.footnotes(referred), edit);
      var children = new ArrayList<>(shown.children());
      children.add(footnotes.element());
      shown = Differ.copy(shown, children);
      inserted += footnotes.inserted();
      deleted += footnotes.deleted();
    }
    return DifferenceBuilder.builder()
        .eId(occurrence.eId())
        .kind(kind)
        .occurrence(sibling(occurrence))
        .shown(shown)
        .inserted(inserted)
        .deleted(deleted)
        .parent(parent)
        .list(occurrence.list())
        .section(occurrence.section())
        .tags(List.of())
        .earlierTags(List.of());
  }

  /**
   * Returns the occurrences directly in {@code occurrence} of {@code occurrences}, or its outermost
   * occurrences.
   */
  private static List<Occurrence> in(Occurrences occurrences, @Nullable Occurrence occurrence) {
    var found = new ArrayList<Occurrence>();
    for (var eId : occurrences.in(occurrence == null ? null : occurrence.eId())) {
      var each = occurrences.get(eId);
      if (each != null) {
        found.add(each);
      }
    }
    return found;
  }

  /** Returns {@code occurrence} as a difference or a fold cites it: its eId, kind and num. */
  static Sibling sibling(Occurrence occurrence) {
    var num =
        occurrence.blocks().stream()
            .filter(block -> block.name().equals("num"))
            .findFirst()
            .map(Wording::of)
            .orElse("");
    var kind = occurrence.element().attribute("kind");
    return new Sibling(occurrence.eId(), kind == null ? "" : kind, num);
  }
}
