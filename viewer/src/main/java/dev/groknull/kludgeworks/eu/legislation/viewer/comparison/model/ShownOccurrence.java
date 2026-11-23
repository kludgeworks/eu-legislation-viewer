// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The content of an occurrence of the later text as a difference shows it, in the order of the
 * text.
 *
 * <ul>
 *   <li>Its own blocks show as its {@link OccurrenceEdits} has them. The blocks deleted after its
 *       last block come right after that block.
 *   <li>A changed occurrence that does not show whole has only its own content: the occurrences in
 *       it differ on their own. A container with only occurrences in it is then left out.
 *   <li>An occurrence that shows whole has the occurrences in it too, each as {@link
 *       ComparedOccurrence#within} shows it. A deleted occurrence shows beside an occurrence that
 *       it stood beside. See {@link #place}.
 *   <li>What a consolidation keeps of a deletion is left out. See {@link Occurrences}.
 * </ul>
 */
final class ShownOccurrence {
  private final boolean withOccurrences;
  private final Pieces.Shown blocks;
  private final int count;
  private final Map<String, ComparedOccurrence> occurrences = new HashMap<>();
  private final Map<String, List<ComparedOccurrence>> deletedBefore = new HashMap<>();
  private final Map<String, List<ComparedOccurrence>> deletedAfter = new HashMap<>();
  private final List<ComparedOccurrence> deletedAlone = new ArrayList<>();
  private int next;

  private ShownOccurrence(ComparedOccurrence node, boolean withOccurrences) {
    var editing = node.editing();
    this.withOccurrences = withOccurrences;
    this.blocks = editing == null ? new Pieces.Shown() : editing.blocks();
    this.count = node.occurrence().blocks().size();
    place(node.occurrences());
  }

  /**
   * Places each deleted occurrence of {@code nodes} beside an occurrence of the later text: before
   * the next such occurrence, or after the last one if there is no next. Thus a deleted item shows
   * in a list.
   */
  private void place(List<ComparedOccurrence> nodes) {
    var waiting = new ArrayList<ComparedOccurrence>();
    @Nullable String last = null;
    for (var each : nodes) {
      if (each.earlier()) {
        waiting.add(each);
        continue;
      }
      last = each.occurrence().eId();
      occurrences.put(last, each);
      if (!waiting.isEmpty()) {
        deletedBefore.put(last, List.copyOf(waiting));
        waiting.clear();
      }
    }
    if (last == null) {
      deletedAlone.addAll(waiting);
    } else {
      deletedAfter.put(last, waiting);
    }
  }

  /**
   * Returns the occurrence of {@code node} as it shows, with the occurrences in it if {@code
   * withOccurrences}.
   */
  static Element of(ComparedOccurrence node, boolean withOccurrences) {
    var content = new ShownOccurrence(node, withOccurrences);
    var element = node.occurrence().element();
    // An occurrence with no blocks left shows its deleted blocks first.
    var children = new ArrayList<>(content.count == 0 ? content.blocks.before(0) : List.of());
    children.addAll(content.in(element));
    if (withOccurrences) {
      // The later occurrence has no occurrences left. Its deleted occurrences go after its
      // blocks.
      children.addAll(listed(content.deletedAlone));
    }
    var editing = node.editing();
    if (editing != null && editing.footnotes() != null) {
      children.add(editing.footnotes());
    }
    return Edits.referring(Differ.copy(element, children));
  }

  private List<Element> in(Element element) {
    var out = new ArrayList<Element>();
    for (var child : element.children()) {
      var eId = Occurrences.eId(child);
      if (child.name().equals("deleted")) {
        continue;
      }
      if (eId != null) {
        if (withOccurrences) {
          out.addAll(occurrence(eId, child));
        }
      } else if (child.name().equals("tag") || child.name().equals("footnotes")) {
        // The footnotes at the end of an annex are not its blocks. See Occurrences.
        out.add(child);
      } else if (Occurrences.holdsOccurrences(child)) {
        var inside = in(child);
        if (!inside.isEmpty()) {
          out.add(Differ.copy(child, inside));
        }
      } else {
        var at = next++;
        out.addAll(blocks.before(at));
        out.add(blocks.at(at, child));
        if (at == count - 1) {
          out.addAll(blocks.before(count));
        }
      }
    }
    return out;
  }

  /**
   * Returns the occurrence {@code eId} in it, with the deleted occurrences that are placed beside
   * it.
   */
  private List<Element> occurrence(String eId, Element element) {
    var out = new ArrayList<Element>();
    deletedBefore.getOrDefault(eId, List.of()).forEach(deleted -> out.add(deleted.within()));
    var node = occurrences.get(eId);
    out.add(node == null ? element : node.within());
    deletedAfter.getOrDefault(eId, List.of()).forEach(deleted -> out.add(deleted.within()));
    return out;
  }

  /**
   * Returns the deleted occurrences of {@code nodes} as they show, each in the list that it was an
   * item of. Occurrences of one list go in one list.
   */
  private static List<Element> listed(List<ComparedOccurrence> nodes) {
    var out = new ArrayList<Element>();
    for (var node : nodes) {
      var list = node.occurrence().list();
      var last = out.isEmpty() ? null : out.getLast();
      if (list == null) {
        out.add(node.within());
      } else if (last != null
          && last.name().equals(list.name())
          && last.attributes().equals(list.attributes())) {
        var items = new ArrayList<>(last.children());
        items.add(node.within());
        out.set(out.size() - 1, Differ.copy(last, items));
      } else {
        out.add(Differ.copy(list, List.of(node.within())));
      }
    }
    return out;
  }
}
