// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Standing;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Edits.Edited;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
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
   * <p>The rule one-difference-for-each-occurrence tells what each occurrence is. The occurrences
   * are in the order of the later text, and each deleted occurrence is where the rule
   * deleted-text-stands-where-it-was puts it. An occurrence that moved out of {@code was} is not
   * deleted: it shows where the later text has it.
   */
  List<ComparedOccurrence> occurrences(@Nullable Occurrence was, @Nullable Occurrence is) {
    var earlier = in(before, was);
    var later = in(after, is);
    var laterOf = new ArrayList<Integer>();
    for (var occurrence : earlier) {
      var now = pairs.is(occurrence);
      laterOf.add(now == null ? -1 : later.indexOf(now));
    }
    // The deleted occurrences that stand before each later occurrence, then those that stand last.
    var deleted = new ArrayList<List<ComparedOccurrence>>();
    for (var j = 0; j <= later.size(); j++) {
      deleted.add(new ArrayList<>());
    }
    for (var i = 0; i < earlier.size(); i++) {
      var occurrence = earlier.get(i);
      var kind =
          WhatCountsAsADifference.oneDifferenceForEachOccurrence(
              true, pairs.is(occurrence) != null, false);
      if (kind.filter(Kind.DELETED::equals).isPresent()) {
        var place = WhatCountsAsADifference.deletedTextStandsWhereItWas(laterOf, i);
        deleted.get(place).add(deleted(occurrence, is, standing(later, place, is)));
      }
    }
    var nodes = new ArrayList<ComparedOccurrence>(deleted.get(0));
    for (var j = 0; j < later.size(); j++) {
      nodes.add(compared(pairs.was(later.get(j)), later.get(j)));
      nodes.addAll(deleted.get(j + 1));
    }
    return nodes;
  }

  /** Returns the occurrence {@code is} of the later text, compared with what it was, if any. */
  private ComparedOccurrence compared(@Nullable Occurrence was, Occurrence is) {
    var editing = was == null ? null : OccurrenceEdits.of(was, is);
    var kind =
        WhatCountsAsADifference.oneDifferenceForEachOccurrence(was != null, true, editing != null);
    if (was == null || kind.filter(Kind.INSERTED::equals).isPresent()) {
      return inserted(is);
    }
    return editing != null && kind.filter(Kind.CHANGED::equals).isPresent()
        ? changed(was, is, editing)
        : ComparedOccurrence.of(is, false, null, null, occurrences(was, is));
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
   * Returns how a difference cites the place of a deleted occurrence that stands at {@code place}
   * among {@code later}, the occurrences of the later occurrence {@code in}: after the occurrence
   * before that place. If it comes first, before the first occurrence. If {@code in} has none,
   * after {@code in}. Null if it comes first among the outermost occurrences and the later text has
   * none: at the end of the text.
   */
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
   * Returns the changed occurrence {@code is} of the later text, with its edits against the
   * occurrence {@code was} of the earlier text that it pairs with.
   */
  private ComparedOccurrence changed(Occurrence was, Occurrence is, OccurrenceEdits editing) {
    var occurrences = occurrences(was, is);
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
    var inserted = kind == Kind.INSERTED;
    var edited = edits(occurrence.element(), inserted);
    var shown = edited.element();
    var insertedWords = edited.inserted();
    var deletedWords = edited.deleted();
    var referred = occurrences.footnotesOf(occurrence.element());
    if (!referred.isEmpty()) {
      var footnotes = edits(Differ.footnotes(referred), inserted);
      var children = new ArrayList<>(shown.children());
      children.add(footnotes.element());
      shown = Differ.copy(shown, children);
      insertedWords += footnotes.inserted();
      deletedWords += footnotes.deleted();
    }
    return DifferenceBuilder.builder()
        .eId(occurrence.eId())
        .kind(kind)
        .occurrence(sibling(occurrence))
        .shown(shown)
        .inserted(insertedWords)
        .deleted(deletedWords)
        .parent(parent)
        .list(occurrence.list())
        .section(occurrence.section())
        .tags(List.of())
        .earlierTags(List.of());
  }

  /** Returns {@code element} with every word in it shown inserted, or shown deleted. */
  private static Edited edits(Element element, boolean inserted) {
    return inserted
        ? WhatCountsAsADifference.whatTheEditsShow(null, element)
        : WhatCountsAsADifference.whatTheEditsShow(element, null);
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
