// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.myers.MyersDiff;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Units.Unit;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * How one text differs from another, as the Changes view shows it (Design: Data the pages need,
 * matching; Mode: Compare): its units matched by eId; a unit in one only, inserted or deleted, with
 * its units; a unit in both whose own wording differs, changed. A changed unit's blocks are aligned
 * by what they say, and a block that changed is marked word by word (Marks): both alignments are
 * Myers's diff (java-diff-utils). Each difference has the opening of the unit it is in, and the
 * units before it there that are the same in both, which the view folds.
 */
public final class Differ {
  private static final Set<String> TITLES = Set.of("num", "heading", "subheading");

  private Differ() {}

  /**
   * The differences of {@code to} from {@code from}, in {@code to}'s order: its units' eIds aligned
   * with {@code from}'s, units deleted before those inserted in their place.
   */
  public static List<Difference> differences(Element from, Element to) {
    var before = Units.of(from);
    var after = Units.of(to);
    var found = new ArrayList<DifferenceBuilder>();
    var units =
        DiffUtils.diff(
            List.copyOf(before.byEId().values()),
            List.copyOf(after.byEId().values()),
            new MyersDiff<Unit>((one, other) -> one.eId().equals(other.eId())),
            null,
            true);
    for (var delta : units.getDeltas()) {
      for (var unit : delta.getSource().getLines()) {
        if (after.get(unit.eId()) == null && in(unit, after)) {
          found.add(whole(unit, Kind.DELETED, Marks.DELETED));
        }
      }
      for (var unit : delta.getTarget().getLines()) {
        var earlier = before.get(unit.eId());
        if (earlier == null) {
          if (in(unit, before)) {
            found.add(whole(unit, Kind.INSERTED, Marks.INSERTED));
          }
        } else {
          // the same unit, where it was or moved
          var changed = changed(earlier, unit);
          if (changed != null) {
            found.add(changed);
          }
        }
      }
    }
    var touched = touched(found, before, after);
    return found.stream().map(difference -> placed(difference, after, touched).build()).toList();
  }

  /** Whether {@code unit} is outermost or the unit it is in is one of {@code units}. */
  private static boolean in(Unit unit, Units units) {
    return unit.parent() == null || units.get(unit.parent()) != null;
  }

  /** {@code unit} whole, every word in it marked {@code mark}. */
  private static DifferenceBuilder whole(Unit unit, Kind kind, String mark) {
    var marked = Marks.all(unit.element(), mark);
    return DifferenceBuilder.builder()
        .eId(unit.eId())
        .kind(kind)
        .shown(marked.element())
        .inserted(marked.inserted())
        .deleted(marked.deleted())
        .parent(unit.parent())
        .section(unit.section());
  }

  /**
   * {@code to} with its own content only, its blocks that {@code from}'s don't say marked: a block
   * changed word by word, one inserted wholly, one deleted wholly where it stood; none if they say
   * the same.
   */
  private static @Nullable DifferenceBuilder changed(Unit from, Unit to) {
    var replaced = new HashMap<Integer, Element>();
    var deletedAt = new HashMap<Integer, List<Element>>();
    var inserted = 0;
    var deleted = 0;
    var deltas = DiffUtils.diff(from.wording(), to.wording()).getDeltas();
    if (deltas.isEmpty()) {
      return null;
    }
    for (var delta : deltas) {
      var source = delta.getSource();
      var target = delta.getTarget();
      var paired = Math.min(source.size(), target.size());
      for (var i = 0; i < target.size(); i++) {
        var block = to.blocks().get(target.getPosition() + i);
        var marked =
            i < paired
                ? Marks.between(from.blocks().get(source.getPosition() + i), block)
                : Marks.all(block, Marks.INSERTED);
        replaced.put(target.getPosition() + i, marked.element());
        inserted += marked.inserted();
        deleted += marked.deleted();
      }
      for (var i = paired; i < source.size(); i++) {
        var marked = Marks.all(from.blocks().get(source.getPosition() + i), Marks.DELETED);
        // before the block after those paired, where it stood
        deletedAt
            .computeIfAbsent(target.getPosition() + paired, at -> new ArrayList<>())
            .add(marked.element());
        deleted += marked.deleted();
      }
    }
    var next = new int[1];
    var children = own(to.element(), replaced, deletedAt, next);
    children.addAll(deletedAt.getOrDefault(next[0], List.of()));
    return DifferenceBuilder.builder()
        .eId(to.eId())
        .kind(Kind.CHANGED)
        .shown(copy(to.element(), children))
        .inserted(inserted)
        .deleted(deleted)
        .parent(to.parent())
        .section(to.section());
  }

  /**
   * {@code element}'s own content (not its units), in Units' order of its blocks: each block {@code
   * replaced} if it is, those {@code deletedAt} it before it; a container with only units in it
   * left out.
   */
  private static List<Element> own(
      Element element,
      Map<Integer, Element> replaced,
      Map<Integer, List<Element>> deletedAt,
      int[] next) {
    var out = new ArrayList<Element>();
    for (var child : element.children()) {
      if (Units.eId(child) != null) {
        continue;
      }
      if (child.name().equals("provenance")) {
        out.add(child);
      } else if (Units.holdsUnits(child)) {
        var inside = own(child, replaced, deletedAt, next);
        if (!inside.isEmpty()) {
          out.add(copy(child, inside));
        }
      } else {
        var at = next[0]++;
        out.addAll(deletedAt.getOrDefault(at, List.of()));
        out.add(replaced.getOrDefault(at, child));
      }
    }
    return out;
  }

  /** The eIds of the units that differ, and of those they are in. */
  private static Set<String> touched(
      List<DifferenceBuilder> differences, Units before, Units after) {
    var touched = new HashSet<String>();
    for (var difference : differences) {
      @Nullable String eId = difference.eId();
      while (eId != null && touched.add(eId)) {
        var unit = after.get(eId);
        unit = unit == null ? before.get(eId) : unit;
        eId = unit == null ? null : unit.parent();
      }
    }
    return touched;
  }

  /**
   * {@code difference} where it stands in {@code after}: the opening of the unit it is in, and the
   * units before it there, back to one {@code touched}, which are the same in both texts.
   */
  private static DifferenceBuilder placed(
      DifferenceBuilder difference, Units after, Set<String> touched) {
    var parent = difference.parent() == null ? null : after.get(difference.parent());
    difference.lead(parent == null ? null : lead(parent));
    var folded = new ArrayList<Sibling>();
    if (difference.kind() != Kind.DELETED) {
      var siblings = after.in(difference.parent());
      for (var i = siblings.indexOf(difference.eId()) - 1; i >= 0; i--) {
        var sibling = after.get(siblings.get(i));
        if (sibling == null || touched.contains(sibling.eId())) {
          break;
        }
        folded.addFirst(sibling(sibling));
      }
    }
    return difference.folded(folded);
  }

  /** {@code unit}'s opening: its num, heading and subheading, and its first other block. */
  private static Element lead(Unit unit) {
    var opening = new ArrayList<Element>();
    for (var block : unit.blocks()) {
      if (TITLES.contains(block.name())) {
        opening.add(block);
      } else {
        opening.add(block);
        break;
      }
    }
    return copy(unit.element(), opening);
  }

  private static Sibling sibling(Unit unit) {
    var num =
        unit.blocks().stream()
            .filter(block -> block.name().equals("num"))
            .findFirst()
            .map(Wording::of)
            .orElse("");
    var kind = unit.element().attribute("kind");
    return new Sibling(unit.eId(), kind == null ? "" : kind, num);
  }

  private static Element copy(Element element, List<Element> children) {
    return new Element(element.name(), element.attributes(), children, element.text());
  }
}
