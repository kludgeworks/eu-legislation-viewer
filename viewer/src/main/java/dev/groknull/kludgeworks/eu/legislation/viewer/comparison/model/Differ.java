// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.myers.MyersDiff;
import com.github.difflib.patch.DeltaType;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
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
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * How one text differs from another, as the Changes view shows it (Design: Data the pages need,
 * matching; Mode: Compare): its units matched by eId; a unit in one only, inserted or deleted, with
 * its units; a unit in both whose own wording differs, changed. A changed unit's blocks are aligned
 * by what they say, and a block that changed is marked word by word (Marks): both alignments are
 * Myers's diff (java-diff-utils).
 *
 * <p>The later text is then walked in order, a deleted unit where it stood, and each of its units
 * placed once: a difference; the opening of a unit that holds differences, as the walk enters it;
 * or, the same in both, with its units, in a run of those in the same unit, folded.
 */
public final class Differ {
  private static final Set<String> TITLES = Set.of("num", "heading", "subheading");

  private Differ() {}

  /**
   * The differences of a later text from an earlier one, and what the later text has after the last
   * of them.
   *
   * @param differences the differences, in the later text's order, each with what comes before it
   * @param closing what the later text has after the last difference; all of it, if none
   */
  public record Compared(List<Difference> differences, List<Context> closing) {
    public Compared {
      differences = List.copyOf(differences);
      closing = List.copyOf(closing);
    }
  }

  /**
   * The differences of {@code to} from {@code from}, in {@code to}'s order: its units' eIds aligned
   * with {@code from}'s, units deleted before those inserted in their place.
   */
  public static Compared differences(Element from, Element to) {
    var before = Units.of(from);
    var after = Units.of(to);
    var steps = steps(before, after);
    var touched =
        touched(
            steps.stream().map(Step::difference).filter(Objects::nonNull).toList(), before, after);
    var placed = new ArrayList<Difference>();
    var context = new ArrayList<Context>();
    var run = new Run();
    // the units placed with the unit they are in: in a fold, or inserted or deleted with it
    var covered = new HashSet<String>();
    for (var step : steps) {
      var unit = step.unit();
      var difference = step.difference();
      if (unit.parent() != null && covered.contains(unit.parent())) {
        covered.add(unit.eId());
      } else if (difference != null) {
        run.into(context);
        var parent = unit.parent() == null ? null : after.get(unit.parent());
        placed.add(difference.lead(parent == null ? null : lead(parent)).context(context).build());
        context = new ArrayList<>();
        if (difference.kind() != Kind.CHANGED) {
          covered.add(unit.eId());
        }
      } else if (step.earlier()) {
        // in a unit deleted, which the walk passed (see in)
        covered.add(unit.eId());
      } else if (touched.contains(unit.eId())) {
        run.into(context);
        context.add(new Context(List.of(sibling(unit)), unit.parent(), lead(unit)));
      } else {
        run.add(unit, context);
        covered.add(unit.eId());
      }
    }
    run.into(context);
    return new Compared(placed, context);
  }

  /**
   * A step of the walk: a unit of the later text, or of the earlier one only, where it stood.
   *
   * @param unit the unit
   * @param earlier whether it is the earlier text's, which the later hasn't
   * @param difference how it differs; none if it doesn't, or is in a unit inserted or deleted
   */
  private record Step(Unit unit, boolean earlier, @Nullable DifferenceBuilder difference) {}

  /**
   * The texts' units in the later text's order, each unit deleted where it stood, before those
   * inserted in its place.
   */
  private static List<Step> steps(Units before, Units after) {
    var steps = new ArrayList<Step>();
    var deltas =
        DiffUtils.diff(
                List.copyOf(before.byEId().values()),
                List.copyOf(after.byEId().values()),
                new MyersDiff<Unit>((one, other) -> one.eId().equals(other.eId())),
                null,
                true)
            .getDeltas();
    for (var delta : deltas) {
      if (delta.getType() != DeltaType.EQUAL) {
        for (var unit : delta.getSource().getLines()) {
          // one the later text has, moved, is placed where it has it
          if (after.get(unit.eId()) == null) {
            steps.add(
                new Step(
                    unit,
                    true,
                    in(unit, after) ? whole(unit, before, Kind.DELETED, Marks.DELETED) : null));
          }
        }
      }
      for (var unit : delta.getTarget().getLines()) {
        var earlier = before.get(unit.eId());
        @Nullable DifferenceBuilder difference;
        if (earlier == null) {
          difference = in(unit, before) ? whole(unit, after, Kind.INSERTED, Marks.INSERTED) : null;
        } else {
          // the same unit, where it was or moved
          difference = changed(earlier, unit, before, after);
        }
        steps.add(new Step(unit, false, difference));
      }
    }
    return steps;
  }

  /** A run of units the same in both texts, in the same unit, waiting to be folded. */
  private static final class Run {
    private final List<Sibling> units = new ArrayList<>();
    private @Nullable String parent;

    /**
     * {@code unit}, after those before it; if it is in another unit, the run folded into {@code
     * context} first.
     */
    void add(Unit unit, List<Context> context) {
      if (!Objects.equals(parent, unit.parent())) {
        into(context);
      }
      units.add(sibling(unit));
      parent = unit.parent();
    }

    /** Folds it into {@code context}, emptying it; nothing if it is empty. */
    void into(List<Context> context) {
      if (!units.isEmpty()) {
        context.add(new Context(units, parent, null));
        units.clear();
      }
    }
  }

  /** Whether {@code unit} is outermost or the unit it is in is one of {@code units}. */
  private static boolean in(Unit unit, Units units) {
    return unit.parent() == null || units.get(unit.parent()) != null;
  }

  /** {@code unit}, of {@code units}, whole, every word in it marked {@code mark}. */
  private static DifferenceBuilder whole(Unit unit, Units units, Kind kind, String mark) {
    var marked = Marks.all(unit.element(), mark);
    return DifferenceBuilder.builder()
        .eId(unit.eId())
        .kind(kind)
        .unit(sibling(unit))
        .shown(marked.element())
        .inserted(marked.inserted())
        .deleted(marked.deleted())
        .parent(unit.parent())
        .section(unit.section())
        .markers(kind == Kind.INSERTED ? Markers.of(unit, units) : List.of())
        .earlierMarkers(kind == Kind.DELETED ? Markers.of(unit, units) : List.of());
  }

  /**
   * {@code to}, of {@code after}, with its own content only, its blocks that {@code from}'s (of
   * {@code before}) don't say marked: a block changed word by word, one inserted wholly, one
   * deleted wholly where it stood; none if they say the same.
   */
  private static @Nullable DifferenceBuilder changed(
      Unit from, Unit to, Units before, Units after) {
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
        .unit(sibling(to))
        .shown(copy(to.element(), children))
        .inserted(inserted)
        .deleted(deleted)
        .parent(to.parent())
        .section(to.section())
        .markers(Markers.of(to, after))
        .earlierMarkers(Markers.of(from, before));
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
