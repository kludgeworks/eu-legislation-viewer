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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * <p>The notes that a unit refers to are compared as part of it. A changed unit shows its notes
 * under it, marked, if they differ. An inserted or deleted unit shows its notes, marked whole.
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
    var alignment = align(before, after);
    var steps = steps(before, after, alignment);
    var differences = new HashMap<String, DifferenceBuilder>();
    for (var step : steps) {
      if (step.difference() != null) {
        differences.putIfAbsent(step.unit().eId(), step.difference());
      }
    }
    var whole = whole(steps, before, after);
    var deletedUnits = deleted(steps, alignment.earlierToLater());
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
        var parentEId =
            step.earlier() ? alignment.earlierToLater().get(unit.parent()) : unit.parent();
        var parent = parentEId == null ? null : after.get(parentEId);
        if (whole.contains(unit.eId())) {
          var shown =
              grouped(
                  unit,
                  new Group(before, after, differences, deletedUnits, alignment.laterToEarlier()),
                  true);
          var inserted = 0;
          var deleted = 0;
          for (var each : steps) {
            var in = each.difference();
            if (in != null && within(each, unit.eId(), before, after, alignment.laterToEarlier())) {
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
   * The alignment of units between the earlier and the later texts.
   *
   * @param earlierToLater the mapping from an earlier unit identifier to a later unit identifier
   * @param laterToEarlier the mapping from a later unit identifier to an earlier unit identifier
   */
  private record Alignment(
      Map<String, String> earlierToLater, Map<String, String> laterToEarlier) {}

  /**
   * Returns the units of the two texts in the order of the later text. Each deleted unit is where
   * it stood, before the units inserted in its place.
   */
  private static List<Step> steps(Units before, Units after, Alignment alignment) {
    var steps = new ArrayList<Step>();
    var deltas =
        DiffUtils.diff(
                List.copyOf(before.byEId().values()),
                List.copyOf(after.byEId().values()),
                new MyersDiff<Unit>(
                    (one, other) ->
                        Objects.equals(alignment.earlierToLater().get(one.eId()), other.eId())),
                null,
                true)
            .getDeltas();
    for (var delta : deltas) {
      if (delta.getType() != DeltaType.EQUAL) {
        for (var unit : delta.getSource().getLines()) {
          // If the later text has the unit, it moved. Place it where the later text has it.
          if (alignment.earlierToLater().get(unit.eId()) == null) {
            steps.add(
                new Step(
                    unit,
                    true,
                    in(unit, after, alignment.earlierToLater())
                        ? whole(unit, before, Kind.DELETED, Marks.DELETED)
                            .markers(Markers.deleted(unit, after, alignment.earlierToLater()))
                        : null));
          }
        }
      }
      for (var unit : delta.getTarget().getLines()) {
        var earlierEId = alignment.laterToEarlier().get(unit.eId());
        var earlier = earlierEId == null ? null : before.get(earlierEId);
        @Nullable DifferenceBuilder difference;
        if (earlier == null) {
          difference =
              in(unit, before, alignment.laterToEarlier())
                  ? whole(unit, after, Kind.INSERTED, Marks.INSERTED)
                  : null;
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
   * Returns whether {@code unit} is in {@code units}. If the unit is outermost, it returns true. If
   * it has a parent, it returns true only when the counterpart of its parent is in {@code units}.
   */
  private static boolean in(Unit unit, Units units, Map<String, String> mapped) {
    if (unit.parent() == null) {
      return true;
    }
    var mappedParent = mapped.get(unit.parent());
    return mappedParent != null && units.get(mappedParent) != null;
  }

  /**
   * Returns {@code unit} of {@code units} as a whole difference, with every word in it marked
   * {@code mark}.
   */
  private static DifferenceBuilder whole(Unit unit, Units units, Kind kind, String mark) {
    var marked = Marks.all(unit.element(), mark);
    var shown = marked.element();
    var inserted = marked.inserted();
    var deleted = marked.deleted();
    var referred = units.notesOf(unit.element());
    if (!referred.isEmpty()) {
      var notes = Marks.all(notes(referred), mark);
      var children = new ArrayList<>(shown.children());
      children.add(notes.element());
      shown = copy(shown, children);
      inserted += notes.inserted();
      deleted += notes.deleted();
    }
    return DifferenceBuilder.builder()
        .eId(unit.eId())
        .kind(kind)
        .unit(sibling(unit))
        .shown(shown)
        .inserted(inserted)
        .deleted(deleted)
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
    if (marking.notes() != null) {
      children.add(marking.notes());
    }
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
   * Returns what the own blocks of {@code unit} say, with its num without the full stop after it,
   * and what the notes that they refer to say. A unit whose note an act changed differs. If a num
   * only gained or lost its full stop, the Publications Office typeset it, and no act changed it.
   * For example, "11.3" to "11.3.".
   */
  private static List<String> numbered(Unit unit) {
    var wording = new ArrayList<String>();
    for (var block : unit.blocks()) {
      var said = Wording.key(block);
      wording.add(block.name().equals("num") ? said.replaceFirst("\\.$", "") : said);
    }
    unit.notes().forEach(note -> wording.add("note:" + Wording.key(note)));
    return wording;
  }

  /**
   * How the own blocks of {@code to} differ from those of {@code from}, by their position among
   * them.
   *
   * @param replaced each block that differs, marked. It is changed word by word, or inserted whole.
   * @param deletedAt the blocks deleted whole, marked, by the position of the block that they stood
   *     before. Blocks deleted after the last block are at the count of blocks.
   * @param notes the notes of {@code to}, marked against those of {@code from}, with the deleted
   *     notes where they stood. Null if the notes say the same.
   * @param inserted how many words are inserted
   * @param deleted how many words are deleted
   */
  private record Marking(
      Map<Integer, Element> replaced,
      Map<Integer, List<Element>> deletedAt,
      @Nullable Element notes,
      int inserted,
      int deleted) {}

  private static Marking marking(Unit from, Unit to) {
    var replaced = new HashMap<Integer, Element>();
    var deletedAt = new HashMap<Integer, List<Element>>();
    var inserted = 0;
    var deleted = 0;
    var deltas = DiffUtils.diff(from.keys(), to.keys()).getDeltas();
    for (var delta : deltas) {
      var source = delta.getSource();
      var target = delta.getTarget();
      var earlier =
          from.blocks().subList(source.getPosition(), source.getPosition() + source.size());
      var later = to.blocks().subList(target.getPosition(), target.getPosition() + target.size());
      var sourceOf = Likeness.pairs(earlier, later);
      var used = new boolean[earlier.size()];
      for (var i = 0; i < later.size(); i++) {
        var block = later.get(i);
        if (sourceOf[i] >= 0) {
          used[sourceOf[i]] = true;
        }
        var marked =
            sourceOf[i] >= 0
                ? Marks.between(earlier.get(sourceOf[i]), block)
                : Marks.all(block, Marks.INSERTED);
        replaced.put(target.getPosition() + i, marked.element());
        inserted += marked.inserted();
        deleted += marked.deleted();
      }
      for (var i = 0; i < earlier.size(); i++) {
        if (used[i]) {
          continue;
        }
        var marked = Marks.all(earlier.get(i), Marks.DELETED);
        // Put it where it stood: before the first block paired with a block after it.
        deletedAt
            .computeIfAbsent(
                target.getPosition() + Likeness.before(sourceOf, i), at -> new ArrayList<>())
            .add(marked.element());
        deleted += marked.deleted();
      }
    }
    @Nullable Element notes = null;
    if (!keys(from.notes()).equals(keys(to.notes()))) {
      var marked = Marks.between(notes(from.notes()), notes(to.notes()));
      notes = marked.element();
      inserted += marked.inserted();
      deleted += marked.deleted();
    }
    return new Marking(replaced, deletedAt, notes, inserted, deleted);
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
   * The deleted units shown within a whole unit, grouped by where they stood.
   *
   * @param before the deleted units that stood before a later unit, by the identifier of that later
   *     unit
   * @param atEnd the deleted units that stood after the last child of a parent, by the parent
   *     identifier
   */
  private record Deleted(Map<String, List<Element>> before, Map<String, List<Element>> atEnd) {

    /**
     * Returns the deleted units that stood before the unit {@code eId}.
     *
     * @param eId the later unit identifier
     * @return the list of deleted elements, or an empty list if none
     */
    List<Element> before(String eId) {
      return before.getOrDefault(eId, List.of());
    }

    /**
     * Returns the deleted units that stood after the last child of {@code parentEId}.
     *
     * @param parentEId the parent unit identifier
     * @return the list of deleted elements, or an empty list if none
     */
    List<Element> atEnd(String parentEId) {
      return atEnd.getOrDefault(parentEId, List.of());
    }
  }

  /**
   * Returns the deleted units, grouped by where they stood in the later text.
   *
   * @param steps the comparison steps in order
   * @param earlierToLater the mapping from earlier to later unit identifiers
   * @return the grouped deleted units
   */
  private static Deleted deleted(List<Step> steps, Map<String, String> earlierToLater) {
    var before = new HashMap<String, List<Element>>();
    var atEnd = new HashMap<String, List<Element>>();
    var pending = new HashMap<@Nullable String, List<Element>>();
    for (var step : steps) {
      var difference = step.difference();
      if (step.earlier()) {
        if (difference != null) {
          var parent =
              step.unit().parent() == null ? null : earlierToLater.get(step.unit().parent());
          if (step.unit().parent() == null || parent != null) {
            pending.computeIfAbsent(parent, p -> new ArrayList<>()).add(difference.shown());
          }
        }
        continue;
      }
      var waiting = pending.remove(step.unit().parent());
      if (waiting != null) {
        before.put(step.unit().eId(), waiting);
      }
    }
    pending.forEach(
        (parent, waiting) -> {
          if (parent != null) {
            atEnd.put(parent, waiting);
          }
        });
    return new Deleted(before, atEnd);
  }

  /**
   * What a unit that shows whole is shown with.
   *
   * @param before the units of the earlier text
   * @param after the units of the later text
   * @param differences how each unit differs, by its eId
   * @param deleted the deleted units, shown, by where they stood
   * @param laterToEarlier the mapping from later unit identifiers to earlier unit identifiers
   */
  private record Group(
      Units before,
      Units after,
      Map<String, DifferenceBuilder> differences,
      Deleted deleted,
      Map<String, String> laterToEarlier) {}

  /** Returns whether the unit of {@code step} is the unit {@code eId}, or is in it. */
  private static boolean within(
      Step step, String eId, Units before, Units after, Map<String, String> laterToEarlier) {
    if (step.earlier()) {
      var earlierEId = laterToEarlier.get(eId);
      if (earlierEId == null) {
        return false;
      }
      for (@Nullable String at = step.unit().eId(); at != null; ) {
        if (at.equals(earlierEId)) {
          return true;
        }
        var unit = before.get(at);
        at = unit == null ? null : unit.parent();
      }
      return false;
    }
    for (@Nullable String at = step.unit().eId(); at != null; ) {
      if (at.equals(eId)) {
        return true;
      }
      var unit = after.get(at);
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
    var earlierEId = group.laterToEarlier().get(unit.eId());
    var earlier = earlierEId == null ? null : group.before().get(earlierEId);
    var marking =
        difference != null && earlier != null && difference.kind() == Kind.CHANGED
            ? marking(earlier, unit)
            : new Marking(Map.of(), Map.of(), null, 0, 0);
    var next = new int[1];
    var children = withUnits(unit.element(), unit, marking, next, group);
    children.addAll(marking.deletedAt().getOrDefault(next[0], List.of()));
    if (marking.notes() != null) {
      children.add(marking.notes());
    }
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
        out.addAll(group.deleted().before(eId));
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
      out.addAll(group.deleted().atEnd(unit.eId()));
    }
    return out;
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

  /** Returns {@code notes} as the notes of a unit, under its blocks and units. */
  private static Element notes(List<Element> notes) {
    return new Element("notes", Map.of(), notes, "");
  }

  /** Returns what each of {@code notes} says, as a comparison matches it. */
  private static List<String> keys(List<Element> notes) {
    return notes.stream().map(Wording::key).toList();
  }

  /**
   * Represents a pair of sibling lists waiting to be aligned.
   *
   * @param earlierIds the identifiers of the earlier sibling units
   * @param laterIds the identifiers of the later sibling units
   * @param earlierParent the identifier of the earlier parent unit, or null if outermost
   * @param laterParent the identifier of the later parent unit, or null if outermost
   */
  private record SiblingScope(
      List<String> earlierIds,
      List<String> laterIds,
      @Nullable String earlierParent,
      @Nullable String laterParent) {}

  /**
   * Aligns the units of {@code before} and {@code after}. Outermost units and child units of paired
   * parents are matched. Units with different identifiers only pair when the later unit carries an
   * action="renumbered" marker.
   *
   * @param before the earlier units
   * @param after the later units
   * @return the alignment between earlier and later units
   */
  private static Alignment align(Units before, Units after) {
    var earlierToLater = new HashMap<String, String>();
    var laterToEarlier = new HashMap<String, String>();
    var queue = new ArrayDeque<SiblingScope>();
    queue.add(new SiblingScope(before.outermost(), after.outermost(), null, null));
    while (!queue.isEmpty()) {
      var scope = queue.removeFirst();
      var childScopes = alignSiblings(scope, before, after, earlierToLater, laterToEarlier);
      queue.addAll(childScopes);
    }
    return new Alignment(earlierToLater, laterToEarlier);
  }

  /**
   * Aligns one list of earlier sibling units with later sibling units.
   *
   * <ul>
   *   <li>First, later units with an action="renumbered" marker align monotonically with earlier
   *       siblings that have different relative identifiers and matching substantive wording.
   *   <li>Next, remaining later units pair with unpaired earlier siblings that share the same
   *       relative identifier.
   * </ul>
   *
   * @param scope the scope of earlier and later sibling units
   * @param before the earlier units
   * @param after the later units
   * @param earlierToLater the mapping from earlier to later identifier
   * @param laterToEarlier the mapping from later to earlier identifier
   * @return the child scopes for paired units
   */
  private static List<SiblingScope> alignSiblings(
      SiblingScope scope,
      Units before,
      Units after,
      Map<String, String> earlierToLater,
      Map<String, String> laterToEarlier) {
    var earlierUnits = new ArrayList<Unit>();
    for (var earlierId : scope.earlierIds()) {
      var unit = before.get(earlierId);
      if (unit != null) {
        earlierUnits.add(unit);
      }
    }
    var laterUnits = new ArrayList<Unit>();
    for (var laterId : scope.laterIds()) {
      var unit = after.get(laterId);
      if (unit != null) {
        laterUnits.add(unit);
      }
    }
    if (earlierUnits.isEmpty() || laterUnits.isEmpty()) {
      return List.of();
    }

    var earlierTexts = new HashMap<String, String>();
    var earlierWordCounts = new HashMap<String, Map<String, Integer>>();
    for (var unit : earlierUnits) {
      var text = substantive(unit);
      earlierTexts.put(unit.eId(), text);
      earlierWordCounts.put(unit.eId(), Likeness.words(text));
    }
    var laterTexts = new HashMap<String, String>();
    var laterWordCounts = new HashMap<String, Map<String, Integer>>();
    for (var unit : laterUnits) {
      var text = substantive(unit);
      laterTexts.put(unit.eId(), text);
      laterWordCounts.put(unit.eId(), Likeness.words(text));
    }

    var pairedEarlier = new HashSet<String>();
    var pairedLater = new HashSet<String>();
    var childScopes = new ArrayList<SiblingScope>();

    var earlierByRelativeId = new HashMap<String, Unit>();
    for (var earlierUnit : earlierUnits) {
      earlierByRelativeId.put(relativeId(earlierUnit.eId(), scope.earlierParent()), earlierUnit);
    }

    // A later unit is considered newly renumbered when its renumbering markers differ from
    // any earlier unit with the same relative identifier.
    var newlyRenumberedLater = new HashSet<String>();
    for (var laterUnit : laterUnits) {
      var laterMarkers = renumberingMarkers(laterUnit);
      if (!laterMarkers.isEmpty()) {
        var earlierSameId =
            earlierByRelativeId.get(relativeId(laterUnit.eId(), scope.laterParent()));
        var earlierMarkers =
            earlierSameId == null ? Set.<RenumberingMarker>of() : renumberingMarkers(earlierSameId);
        if (!earlierMarkers.equals(laterMarkers)) {
          newlyRenumberedLater.add(laterUnit.eId());
        }
      }
    }

    // Pass 1: Monotonically align later units carrying a renumbered marker
    alignRenumbered(
        earlierUnits,
        laterUnits,
        newlyRenumberedLater,
        scope,
        earlierTexts,
        laterTexts,
        earlierWordCounts,
        laterWordCounts,
        pairedEarlier,
        pairedLater,
        earlierToLater,
        laterToEarlier,
        childScopes,
        before,
        after);

    // Pass 2: Pair remaining units that share the same relative identifier
    var unpairedEarlierByRelativeId = new HashMap<String, Unit>();
    for (var earlierUnit : earlierUnits) {
      if (!pairedEarlier.contains(earlierUnit.eId())) {
        unpairedEarlierByRelativeId.put(
            relativeId(earlierUnit.eId(), scope.earlierParent()), earlierUnit);
      }
    }
    for (var laterUnit : laterUnits) {
      if (pairedLater.contains(laterUnit.eId()) || newlyRenumberedLater.contains(laterUnit.eId())) {
        continue;
      }
      var relativeLater = relativeId(laterUnit.eId(), scope.laterParent());
      var earlierUnit = unpairedEarlierByRelativeId.get(relativeLater);
      if (earlierUnit != null && !pairedEarlier.contains(earlierUnit.eId())) {
        pairedEarlier.add(earlierUnit.eId());
        pairedLater.add(laterUnit.eId());
        earlierToLater.put(earlierUnit.eId(), laterUnit.eId());
        laterToEarlier.put(laterUnit.eId(), earlierUnit.eId());
        childScopes.add(
            new SiblingScope(
                before.in(earlierUnit.eId()),
                after.in(laterUnit.eId()),
                earlierUnit.eId(),
                laterUnit.eId()));
      }
    }

    return childScopes;
  }

  /**
   * Monotonically aligns later units carrying a renumbered marker with earlier sibling units.
   *
   * <p>Renumbering preserves the relative order of provisions. An earlier sibling is only a
   * candidate if its relative identifier differs from the later unit and their wording similarity
   * is at least {@link Likeness#MINIMUM}.
   */
  private static void alignRenumbered(
      List<Unit> earlierUnits,
      List<Unit> laterUnits,
      Set<String> newlyRenumberedLater,
      SiblingScope scope,
      Map<String, String> earlierTexts,
      Map<String, String> laterTexts,
      Map<String, Map<String, Integer>> earlierWordCounts,
      Map<String, Map<String, Integer>> laterWordCounts,
      Set<String> pairedEarlier,
      Set<String> pairedLater,
      Map<String, String> earlierToLater,
      Map<String, String> laterToEarlier,
      List<SiblingScope> childScopes,
      Units before,
      Units after) {
    var renumberedLater = new ArrayList<Unit>();
    for (var laterUnit : laterUnits) {
      if (newlyRenumberedLater.contains(laterUnit.eId())) {
        renumberedLater.add(laterUnit);
      }
    }
    if (renumberedLater.isEmpty()) {
      return;
    }

    int n = renumberedLater.size();
    int m = earlierUnits.size();
    var dp = new double[n + 1][m + 1];
    var choice = new byte[n + 1][m + 1];

    for (int j = 1; j <= n; j++) {
      var laterUnit = renumberedLater.get(j - 1);
      var relativeLater = relativeId(laterUnit.eId(), scope.laterParent());
      var laterCounts = laterWordCounts.getOrDefault(laterUnit.eId(), Map.of());
      var laterText = laterTexts.getOrDefault(laterUnit.eId(), "");

      for (int i = 1; i <= m; i++) {
        var earlierUnit = earlierUnits.get(i - 1);
        var relativeEarlier = relativeId(earlierUnit.eId(), scope.earlierParent());

        double bestScore = dp[j][i - 1];
        byte bestChoice = 3;

        if (dp[j - 1][i] > bestScore) {
          bestScore = dp[j - 1][i];
          bestChoice = 2;
        }

        if (!Objects.equals(relativeEarlier, relativeLater)) {
          var earlierText = earlierTexts.getOrDefault(earlierUnit.eId(), "");
          double score = 0.0;
          if (!laterText.isEmpty() && earlierText.equals(laterText)) {
            score = 1.0;
          } else {
            var sim =
                Likeness.of(
                    earlierWordCounts.getOrDefault(earlierUnit.eId(), Map.of()), laterCounts);
            if (sim >= Likeness.MINIMUM) {
              score = sim;
            }
          }
          if (score >= Likeness.MINIMUM) {
            double candidateScore = dp[j - 1][i - 1] + score;
            if (candidateScore > bestScore) {
              bestScore = candidateScore;
              bestChoice = 1;
            }
          }
        }

        dp[j][i] = bestScore;
        choice[j][i] = bestChoice;
      }
    }

    int j = n;
    int i = m;
    var matchedEarlier = new ArrayList<Unit>();
    var matchedLater = new ArrayList<Unit>();
    while (j > 0 && i > 0) {
      if (choice[j][i] == 1) {
        matchedEarlier.add(earlierUnits.get(i - 1));
        matchedLater.add(renumberedLater.get(j - 1));
        j--;
        i--;
      } else if (choice[j][i] == 2) {
        j--;
      } else {
        i--;
      }
    }

    for (int k = 0; k < matchedEarlier.size(); k++) {
      var earlier = matchedEarlier.get(k);
      var later = matchedLater.get(k);
      pairedEarlier.add(earlier.eId());
      pairedLater.add(later.eId());
      earlierToLater.put(earlier.eId(), later.eId());
      laterToEarlier.put(later.eId(), earlier.eId());
      childScopes.add(
          new SiblingScope(
              before.in(earlier.eId()), after.in(later.eId()), earlier.eId(), later.eId()));
    }
  }

  /**
   * Returns the relative identifier of {@code eId} with respect to its parent identifier {@code
   * parent}.
   *
   * @param eId the unit identifier
   * @param parent the parent identifier, or null if outermost
   * @return the relative identifier
   */
  private static String relativeId(String eId, @Nullable String parent) {
    if (parent != null && eId.startsWith(parent + "/")) {
      return eId.substring(parent.length() + 1);
    }
    return eId;
  }

  /**
   * The amending act and marker identifier of a renumbering marker.
   *
   * @param amendingAct the amending act, or null if none
   * @param marker the marker identifier, or null if none
   */
  private record RenumberingMarker(@Nullable String amendingAct, @Nullable String marker) {}

  /**
   * Returns the renumbering markers on the provenance or number of {@code unit}.
   *
   * @param unit the unit to inspect
   * @return the set of renumbering markers
   */
  private static Set<RenumberingMarker> renumberingMarkers(Unit unit) {
    var markers = new HashSet<RenumberingMarker>();
    for (var child : unit.element().children()) {
      if ("provenance".equals(child.name()) && "renumbered".equals(child.attribute("action"))) {
        markers.add(
            new RenumberingMarker(child.attribute("amendingAct"), child.attribute("marker")));
      }
    }
    for (var block : unit.blocks()) {
      if ("num".equals(block.name())) {
        collectRenumberingMarkers(block, markers);
      }
    }
    return markers;
  }

  /**
   * Collects all renumbering markers from {@code element} and its non-unit descendant elements into
   * {@code markers}.
   *
   * @param element the element to inspect
   * @param markers the set to collect markers into
   */
  private static void collectRenumberingMarkers(Element element, Set<RenumberingMarker> markers) {
    if ("renumbered".equals(element.attribute("action"))) {
      markers.add(
          new RenumberingMarker(element.attribute("amendingAct"), element.attribute("marker")));
    }
    for (var child : element.children()) {
      if (Units.eId(child) == null) {
        collectRenumberingMarkers(child, markers);
      }
    }
  }

  /**
   * Returns the substantive words of {@code unit}, excluding its titles, headings and child units.
   *
   * @param unit the unit
   * @return the substantive text in lower case
   */
  private static String substantive(Unit unit) {
    var builder = new StringBuilder();
    for (var block : unit.blocks()) {
      if (!TITLES.contains(block.name())) {
        collectSubstantiveText(block, builder);
      }
    }
    return builder.toString().strip().toLowerCase(Locale.ROOT);
  }

  /**
   * Recursively collects substantive text from {@code element} into {@code builder}, ignoring child
   * units.
   *
   * @param element the element to collect text from
   * @param builder the string builder to append text into
   */
  private static void collectSubstantiveText(Element element, StringBuilder builder) {
    if ("t".equals(element.name())) {
      var value = element.attribute("v");
      if (value != null && !value.isBlank()) {
        if (!builder.isEmpty()) {
          builder.append(' ');
        }
        builder.append(value);
      }
      return;
    }
    for (var child : element.children()) {
      if (Units.eId(child) == null) {
        collectSubstantiveText(child, builder);
      }
    }
  }
}
