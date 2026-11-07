// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.myers.MyersDiff;
import com.github.difflib.patch.DeltaType;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
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
 *
 * <p>What counts as one difference (Design: What counts as one difference): a changed unit and the
 * units in it that differ, where the same acts made them all, are one, shown whole, each marked
 * where it differs (11.1.3 with its points (a) to (d), all 2019/103's); a unit whose num only
 * gained or lost its full stop (11.3 to 11.3.) differs in nothing an act did, so not at all.
 */
public final class Differ {
  private static final Set<String> TITLES = Set.of("num", "heading", "subheading");

  /** Where a unit's units end, after its eId: what was deleted after the last of them. */
  private static final String END = "#end";

  private Differ() {}

  /**
   * The differences of a later text from an earlier one, and what the later text has after the last
   * of them.
   *
   * @param differences the differences, in the later text's order, each with what comes before it
   * @param closing what the later text has after the last difference; all of it, if none
   * @param withoutPreamble the text without a preamble, where the other has one, which isn't
   *     compared; none if both have one or neither does
   */
  public record Compared(
      List<Difference> differences, List<Context> closing, @Nullable Side withoutPreamble) {
    public Compared {
      differences = List.copyOf(differences);
      closing = List.copyOf(closing);
    }
  }

  /**
   * The differences of {@code to} from {@code from}, in {@code to}'s order: its units' eIds aligned
   * with {@code from}'s, units deleted before those inserted in their place. A preamble one of them
   * has and the other hasn't isn't compared: the Publications Office publishes some consolidations
   * without it (2015/1998's from 2017), which no act did.
   */
  public static Compared differences(Element from, Element to) {
    var earlierPreamble = preamble(from);
    var laterPreamble = preamble(to);
    @Nullable Side withoutPreamble =
        earlierPreamble == laterPreamble ? null : earlierPreamble ? Side.LATER : Side.EARLIER;
    var before = Units.of(withoutPreamble == Side.LATER ? withoutPreamble(from) : from);
    var after = Units.of(withoutPreamble == Side.EARLIER ? withoutPreamble(to) : to);
    var steps = steps(before, after);
    var differences = new HashMap<String, DifferenceBuilder>();
    for (var step : steps) {
      if (step.difference() != null) {
        differences.putIfAbsent(step.unit().eId(), step.difference());
      }
    }
    var whole = whole(steps, before, after);
    var deletedBefore = deletedBefore(steps);
    var touched = touched(List.copyOf(differences.values()), before, after);
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
        if (whole.contains(unit.eId())) {
          var shown = grouped(unit, new Group(before, after, differences, deletedBefore), true);
          var inserted = 0;
          var deleted = 0;
          for (var each : steps) {
            var in = each.difference();
            if (in != null && within(each, unit.eId(), before, after)) {
              inserted += in.inserted();
              deleted += in.deleted();
            }
          }
          difference.shown(shown).inserted(inserted).deleted(deleted);
        }
        placed.add(difference.lead(parent == null ? null : lead(parent)).context(context).build());
        context = new ArrayList<>();
        if (difference.kind() != Kind.CHANGED || whole.contains(unit.eId())) {
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
    return new Compared(placed, context, withoutPreamble);
  }

  /** Whether {@code text} has a preamble with units in it: its citations, its recitals. */
  private static boolean preamble(Element text) {
    if (text.name().equals("preamble")) {
      return Units.holdsUnits(text);
    }
    return text.children().stream().anyMatch(Differ::preamble);
  }

  /** {@code element} without its preamble. */
  private static Element withoutPreamble(Element element) {
    var children = new ArrayList<Element>();
    for (var child : element.children()) {
      if (!child.name().equals("preamble")) {
        children.add(withoutPreamble(child));
      }
    }
    return copy(element, children);
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
                    in(unit, after)
                        ? whole(unit, before, Kind.DELETED, Marks.DELETED)
                            .markers(Markers.deleted(unit, after))
                        : null));
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
        .list(unit.list())
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
    if (numbered(from).equals(numbered(to))) {
      return null;
    }
    var marking = marking(from, to);
    var children = own(to.element(), marking.replaced(), marking.deletedAt(), new int[1]);
    return DifferenceBuilder.builder()
        .eId(to.eId())
        .kind(Kind.CHANGED)
        .unit(sibling(to))
        .shown(copy(to.element(), children))
        .inserted(marking.inserted())
        .deleted(marking.deleted())
        .parent(to.parent())
        .list(to.list())
        .section(to.section())
        .markers(Markers.of(to, after))
        .earlierMarkers(Markers.of(from, before));
  }

  /**
   * What {@code unit}'s own blocks say, its num without the full stop after it: a num that only
   * gained or lost one (11.3 to 11.3.) is the Publications Office's typesetting, not an act's.
   */
  private static List<String> numbered(Unit unit) {
    var wording = new ArrayList<String>();
    for (var block : unit.blocks()) {
      var said = Wording.of(block);
      wording.add(block.name().equals("num") ? said.strip().replaceFirst("\\.$", "") : said);
    }
    return wording;
  }

  /**
   * How {@code to}'s own blocks differ from {@code from}'s, by their position among them.
   *
   * @param replaced each block that differs, marked: changed word by word, or inserted wholly
   * @param deletedAt the blocks deleted wholly, marked, before the block where they stood (after
   *     the last, at the count of blocks)
   * @param inserted how many words are inserted
   * @param deleted how many words are deleted
   */
  private record Marking(
      Map<Integer, Element> replaced,
      Map<Integer, List<Element>> deletedAt,
      int inserted,
      int deleted) {}

  private static Marking marking(Unit from, Unit to) {
    var replaced = new HashMap<Integer, Element>();
    var deletedAt = new HashMap<Integer, List<Element>>();
    var inserted = 0;
    var deleted = 0;
    var deltas = DiffUtils.diff(from.wording(), to.wording()).getDeltas();
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
    return new Marking(replaced, deletedAt, inserted, deleted);
  }

  /**
   * The changed units shown whole, with the units in them that differ: those where every difference
   * in them is made by the acts that made theirs.
   */
  private static Set<String> whole(List<Step> steps, Units before, Units after) {
    var acts = new HashMap<String, Set<String>>();
    var in = new HashMap<String, Boolean>();
    for (var step : steps) {
      var difference = step.difference();
      if (difference == null) {
        continue;
      }
      var made = acts(difference);
      // each changed unit it is in: whether the same acts made it
      var units = step.earlier() ? before : after;
      var parent = step.unit().parent();
      while (parent != null) {
        var opened = acts.get(parent);
        if (opened != null) {
          in.merge(parent, opened.equals(made), Boolean::logicalAnd);
        }
        var unit = units.get(parent);
        parent = unit == null ? null : unit.parent();
      }
      if (difference.kind() == Kind.CHANGED) {
        acts.putIfAbsent(step.unit().eId(), made);
      }
    }
    var whole = new HashSet<String>();
    in.forEach(
        (eId, same) -> {
          if (same) {
            whole.add(eId);
          }
        });
    return whole;
  }

  /** The acts that made {@code difference}: its markers', or none if it has none. */
  private static Set<String> acts(DifferenceBuilder difference) {
    var acts = new HashSet<String>();
    for (var marker : difference.markers()) {
      acts.add(marker.act());
    }
    return acts;
  }

  /**
   * The units deleted, as they differ, by the eId of the later text's unit they stood before (or of
   * the unit they were in, with END, after its last).
   */
  private static Map<String, List<DifferenceBuilder>> deletedBefore(List<Step> steps) {
    var deletedBefore = new HashMap<String, List<DifferenceBuilder>>();
    var pending = new HashMap<@Nullable String, List<DifferenceBuilder>>();
    for (var step : steps) {
      var difference = step.difference();
      if (step.earlier()) {
        if (difference != null) {
          pending
              .computeIfAbsent(step.unit().parent(), parent -> new ArrayList<>())
              .add(difference);
        }
        continue;
      }
      var waiting = pending.remove(step.unit().parent());
      if (waiting != null) {
        deletedBefore.put(step.unit().eId(), waiting);
      }
    }
    pending.forEach(
        (parent, waiting) -> {
          if (parent != null) {
            deletedBefore.put(parent + END, waiting);
          }
        });
    return deletedBefore;
  }

  /**
   * What a unit shown whole is shown with.
   *
   * @param before the earlier text's units
   * @param after the later text's
   * @param differences how each unit differs, by its eId
   * @param deletedBefore the units deleted, shown, by where they stood (deletedBefore)
   */
  private record Group(
      Units before,
      Units after,
      Map<String, DifferenceBuilder> differences,
      Map<String, List<DifferenceBuilder>> deletedBefore) {}

  /** Whether {@code step}'s unit is the unit {@code eId} or in it. */
  private static boolean within(Step step, String eId, Units before, Units after) {
    var units = step.earlier() ? before : after;
    for (@Nullable String at = step.unit().eId(); at != null; ) {
      if (at.equals(eId)) {
        return true;
      }
      var unit = units.get(at);
      at = unit == null ? null : unit.parent();
    }
    return false;
  }

  /**
   * {@code unit} whole, of the later text: its own blocks marked where they differ, and each unit
   * in it: inserted, deleted where it stood, changed, each marked; the same in both, as it is.
   */
  private static Element grouped(Unit unit, Group group, boolean outermost) {
    var difference = group.differences().get(unit.eId());
    if (difference != null && difference.kind() == Kind.INSERTED && !outermost) {
      return difference.shown();
    }
    var earlier = group.before().get(unit.eId());
    var marking =
        difference != null && earlier != null && difference.kind() == Kind.CHANGED
            ? marking(earlier, unit)
            : new Marking(Map.of(), Map.of(), 0, 0);
    var next = new int[1];
    var children = withUnits(unit.element(), unit, marking, next, group);
    children.addAll(marking.deletedAt().getOrDefault(next[0], List.of()));
    return copy(unit.element(), children);
  }

  /**
   * {@code element}'s content, of {@code unit}, as own does, with the units in it: each shown as
   * grouped shows it, those deleted before it where they stood.
   */
  private static List<Element> withUnits(
      Element element, Unit unit, Marking marking, int[] next, Group group) {
    var out = new ArrayList<Element>();
    var units = false;
    for (var child : element.children()) {
      var eId = Units.eId(child);
      if (child.name().equals("deleted")) {
        continue;
      }
      if (eId != null) {
        units = true;
        deleted(group, eId, out);
        var inside = group.after().get(eId);
        out.add(inside == null ? child : grouped(inside, group, false));
      } else if (child.name().equals("provenance")) {
        out.add(child);
      } else if (Units.holdsUnits(child)) {
        var inside = withUnits(child, unit, marking, next, group);
        if (!inside.isEmpty()) {
          out.add(copy(child, inside));
        }
      } else {
        var at = next[0]++;
        out.addAll(marking.deletedAt().getOrDefault(at, List.of()));
        out.add(marking.replaced().getOrDefault(at, child));
      }
    }
    if (units) {
      deleted(group, unit.eId() + END, out);
    }
    return out;
  }

  /** The units deleted that stood at {@code where}, shown, to {@code out}. */
  private static void deleted(Group group, String where, List<Element> out) {
    for (var each : group.deletedBefore().getOrDefault(where, List.of())) {
      out.add(each.shown());
    }
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
      // a deletion's struck words: not the text's (Units)
      if (Units.eId(child) != null || child.name().equals("deleted")) {
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
