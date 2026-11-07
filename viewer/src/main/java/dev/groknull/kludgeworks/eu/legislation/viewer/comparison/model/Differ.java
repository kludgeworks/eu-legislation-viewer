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
 * Finds how one text differs from another, as the Changes view shows it.
 *
 * <ul>
 *   <li>The units of the two texts are matched by eId.
 *   <li>A unit in only one text is inserted or deleted, with its units.
 *   <li>A unit in both texts whose own wording differs is changed.
 * </ul>
 *
 * <p>The blocks of a changed unit are aligned by what they say. {@link Marks} marks a changed block
 * word by word. Both alignments use the Myers diff of java-diff-utils.
 *
 * <p>Then the walk goes through the later text in order, with each deleted unit where it stood. It
 * places each unit once, as one of these:
 *
 * <ul>
 *   <li>a difference
 *   <li>the opening of a unit that holds differences, when the walk enters it
 *   <li>a unit that is the same in both texts, with its units. It is folded in a run of such units
 *       in the same unit.
 * </ul>
 *
 * <p>These rules decide what counts as one difference:
 *
 * <ul>
 *   <li>A changed unit and the units in it that differ are one difference if the same acts made all
 *       of them. The difference shows whole, and each unit is marked where it differs. For example,
 *       11.1.3 with its points (a) to (d), all from 2019/103.
 *   <li>If the num of a unit only gained or lost its full stop, no act changed it. Thus the unit
 *       does not differ. For example, "11.3" to "11.3.".
 * </ul>
 */
public final class Differ {
  private static final Set<String> TITLES = Set.of("num", "heading", "subheading");

  /**
   * The suffix, after the eId of a unit, that marks where its units end. The units deleted after
   * the last of its units go there.
   */
  private static final String END = "#end";

  private Differ() {}

  /**
   * The differences of a later text from an earlier one, and what the later text has after the last
   * difference.
   *
   * @param differences the differences, in the order of the later text, each with what comes before
   *     it
   * @param closing what the later text has after the last difference. All of the later text if
   *     there are no differences.
   * @param withoutPreamble the text without a preamble, if the other text has one. The preamble is
   *     then not compared. Null if both texts have one, or if neither has one.
   */
  public record Compared(
      List<Difference> differences, List<Context> closing, @Nullable Side withoutPreamble) {
    public Compared {
      differences = List.copyOf(differences);
      closing = List.copyOf(closing);
    }
  }

  /**
   * Returns the differences of {@code to} from {@code from}, in the order of {@code to}. The eIds
   * of the units of {@code to} are aligned with those of {@code from}. Deleted units come before
   * the units inserted in their place.
   *
   * <p>If only one text has a preamble, the preamble is not compared. The Publications Office
   * publishes some consolidations without the preamble, although no act removed it. For example,
   * the consolidations of 2015/1998 from 2017.
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
    // The units placed with the unit that they are in: in a fold, or inserted or deleted with it.
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
        // The unit is in a deleted unit that the walk passed. See the method in.
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

  /**
   * Returns whether {@code text} has a preamble with units in it. The units are its citations and
   * its recitals.
   */
  private static boolean preamble(Element text) {
    if (text.name().equals("preamble")) {
      return Units.holdsUnits(text);
    }
    return text.children().stream().anyMatch(Differ::preamble);
  }

  /** Returns {@code element} without its preamble. */
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
   * A step of the walk. It is a unit of the later text, or a unit of only the earlier text where it
   * stood.
   *
   * @param unit the unit
   * @param earlier whether the unit is of the earlier text, and the later text does not have it
   * @param difference how it differs. Null if it does not differ, or if it is in an inserted or
   *     deleted unit.
   */
  private record Step(Unit unit, boolean earlier, @Nullable DifferenceBuilder difference) {}

  /**
   * Returns the units of the two texts in the order of the later text. Each deleted unit is where
   * it stood, before the units inserted in its place.
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
          // If the later text has the unit, it moved. Place it where the later text has it.
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
          // The same unit, in the same place or moved.
          difference = changed(earlier, unit, before, after);
        }
        steps.add(new Step(unit, false, difference));
      }
    }
    return steps;
  }

  /**
   * A run of units that are the same in both texts and in the same unit. The run waits to be
   * folded.
   */
  private static final class Run {
    private final List<Sibling> units = new ArrayList<>();
    private @Nullable String parent;

    /**
     * Adds {@code unit} after the units before it. If it is in another unit, it first folds the run
     * into {@code context}.
     */
    void add(Unit unit, List<Context> context) {
      if (!Objects.equals(parent, unit.parent())) {
        into(context);
      }
      units.add(sibling(unit));
      parent = unit.parent();
    }

    /** Folds the run into {@code context} and empties it. Does nothing if the run is empty. */
    void into(List<Context> context) {
      if (!units.isEmpty()) {
        context.add(new Context(units, parent, null));
        units.clear();
      }
    }
  }

  /**
   * Returns whether {@code unit} is outermost, or the unit that it is in is one of {@code units}.
   */
  private static boolean in(Unit unit, Units units) {
    return unit.parent() == null || units.get(unit.parent()) != null;
  }

  /**
   * Returns {@code unit} of {@code units} as a whole difference, with every word in it marked
   * {@code mark}.
   */
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
   * Returns {@code to} of {@code after} as a changed difference, with only its own content. The
   * blocks that differ from those of {@code from} of {@code before} are marked.
   *
   * <ul>
   *   <li>A changed block is marked word by word.
   *   <li>An inserted block is marked whole.
   *   <li>A deleted block is marked whole, where it stood.
   * </ul>
   *
   * <p>Returns null if the two units say the same.
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
   * Returns what the own blocks of {@code unit} say, with its num without the full stop after it.
   * If a num only gained or lost its full stop, the Publications Office typeset it, and no act
   * changed it. For example, "11.3" to "11.3.".
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
   * How the own blocks of {@code to} differ from those of {@code from}, by their position among
   * them.
   *
   * @param replaced each block that differs, marked. It is changed word by word, or inserted whole.
   * @param deletedAt the blocks deleted whole, marked, by the position of the block that they stood
   *     before. Blocks deleted after the last block are at the count of blocks.
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
        // Put it before the block after the paired blocks, where it stood.
        deletedAt
            .computeIfAbsent(target.getPosition() + paired, at -> new ArrayList<>())
            .add(marked.element());
        deleted += marked.deleted();
      }
    }
    return new Marking(replaced, deletedAt, inserted, deleted);
  }

  /**
   * Returns the eIds of the changed units that show whole, with the units in them that differ. A
   * unit shows whole if the acts that changed it also made every difference in it.
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
      // For each changed unit that it is in, record whether the same acts made it.
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

  /**
   * Returns the acts that made {@code difference}, from its markers. Returns an empty set if it has
   * no markers.
   */
  private static Set<String> acts(DifferenceBuilder difference) {
    var acts = new HashSet<String>();
    for (var marker : difference.markers()) {
      acts.add(marker.act());
    }
    return acts;
  }

  /**
   * Returns the deleted units as differences, by the eId of the unit of the later text that they
   * stood before. If they stood after the last unit, the key is the eId of the unit that they were
   * in, followed by {@link #END}.
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
   * What a unit that shows whole is shown with.
   *
   * @param before the units of the earlier text
   * @param after the units of the later text
   * @param differences how each unit differs, by its eId
   * @param deletedBefore the deleted units, shown, by where they stood. See {@link #deletedBefore}.
   */
  private record Group(
      Units before,
      Units after,
      Map<String, DifferenceBuilder> differences,
      Map<String, List<DifferenceBuilder>> deletedBefore) {}

  /** Returns whether the unit of {@code step} is the unit {@code eId}, or is in it. */
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
   * Returns {@code unit} of the later text, whole. Its own blocks are marked where they differ.
   *
   * <ul>
   *   <li>An inserted unit in it is marked.
   *   <li>A deleted unit in it is marked, where it stood.
   *   <li>A changed unit in it is marked.
   *   <li>A unit that is the same in both texts shows as it is.
   * </ul>
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
   * Returns the content of {@code element} of {@code unit}, as {@link #own} does, but with the
   * units in it. Each unit shows as {@link #grouped} shows it. The deleted units show where they
   * stood.
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

  /** Adds the shown deleted units that stood at {@code where} to {@code out}. */
  private static void deleted(Group group, String where, List<Element> out) {
    for (var each : group.deletedBefore().getOrDefault(where, List.of())) {
      out.add(each.shown());
    }
  }

  /**
   * Returns the own content of {@code element}, without its units. The blocks are in the order of
   * {@link Units}.
   *
   * <ul>
   *   <li>A block in {@code replaced} is replaced.
   *   <li>The blocks in {@code deletedAt} for a position go before the block at that position.
   *   <li>A container with only units in it is left out.
   * </ul>
   */
  private static List<Element> own(
      Element element,
      Map<Integer, Element> replaced,
      Map<Integer, List<Element>> deletedAt,
      int[] next) {
    var out = new ArrayList<Element>();
    for (var child : element.children()) {
      // The struck words of a deletion are not part of the text. See Units.
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

  /** Returns the eIds of the units that differ, and of the units that they are in. */
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
   * Returns the opening of {@code unit}: its num, heading and subheading, and its first other
   * block.
   */
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
