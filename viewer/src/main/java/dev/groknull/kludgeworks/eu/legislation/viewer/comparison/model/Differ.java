// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

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
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Finds how one text differs from another, as the Changes view shows it. It works in four stages:
 *
 * <ol>
 *   <li>{@link Units} reads the units of each text.
 *   <li>{@link Pairing} pairs the units of the two texts: siblings with siblings, and a unit that
 *       moved into the unit before it, or out of it.
 *   <li>The units are compared, from the outermost units inwards. A unit in only one text is
 *       inserted or deleted, with its units. A unit in both texts whose own wording differs is
 *       changed, and the units in it are compared in turn.
 *   <li>The differences are shown in the order of the later text, each with what the later text has
 *       before it.
 * </ol>
 *
 * <p>The units of the two texts are in one order: the units of the later text in their order, and
 * each deleted unit after the unit that it stood after. Thus a deleted unit comes before the units
 * inserted in its place.
 *
 * <p>The notes that a unit refers to are compared as part of it. A changed unit shows its notes
 * under it, marked, if they differ. An inserted or deleted unit shows its notes, marked whole.
 *
 * <p>The own blocks of a changed unit are matched by what they say ({@link Parts}). {@link Marks}
 * marks a changed block word by word.
 *
 * <p>Each unit of the later text is placed once, as one of these:
 *
 * <ul>
 *   <li>a difference
 *   <li>the opening of a unit that holds differences
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
   * Returns the differences of {@code to} from {@code from}, in the order of {@code to}. Each
   * deleted unit is where it stood, before the units inserted in its place.
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
    var show = new Show();
    show.units(new Compare(before, after, Pairing.of(before, after)).units(null, null), null);
    return new Compared(show.placed, show.context, withoutPreamble);
  }

  /**
   * Returns whether {@code text} has a preamble with units in it. The units are its citations and
   * its recitals. Legal-text has the preamble only in the text itself, between its preface and its
   * body, so the search stops there.
   */
  private static boolean preamble(Element text) {
    if (text.name().equals("preamble")) {
      return Units.holdsUnits(text);
    }
    for (var child : text.children()) {
      if (child.name().equals("preamble") && Units.holdsUnits(child)) {
        return true;
      }
    }
    return false;
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
   * A unit of the two texts, compared.
   *
   * @param unit the unit, as the later text has it. For a deleted unit, as the earlier text has it.
   * @param earlier whether the unit is of only the earlier text: it is deleted
   * @param difference how the unit differs. Null if its own wording is the same in both texts.
   * @param marking how its own blocks and notes differ. Null if the unit is not changed.
   * @param units the units in it, compared, in the one order of the two texts. Empty for an
   *     inserted or deleted unit, which is one difference with its units.
   * @param differs whether the unit differs, or a unit in it does
   */
  private record Node(
      Unit unit,
      boolean earlier,
      @Nullable DifferenceBuilder difference,
      @Nullable Marking marking,
      List<Node> units,
      boolean differs) {

    static Node of(
        Unit unit,
        boolean earlier,
        @Nullable DifferenceBuilder difference,
        @Nullable Marking marking,
        List<Node> units) {
      var differs = difference != null || units.stream().anyMatch(Node::differs);
      return new Node(unit, earlier, difference, marking, units, differs);
    }

    /**
     * Returns whether the unit shows whole, as one difference with the units in it that differ. It
     * does if it is changed, and the acts that changed it also made every difference in it.
     */
    boolean showsWhole() {
      return difference != null
          && difference.kind() == Kind.CHANGED
          && units.stream().anyMatch(Node::differs)
          && madeBy(acts(difference));
    }

    /** Returns whether {@code acts} made every difference in the unit. */
    private boolean madeBy(Set<String> acts) {
      for (var each : units) {
        var in = each.difference();
        if ((in != null && !acts(in).equals(acts)) || !each.madeBy(acts)) {
          return false;
        }
      }
      return true;
    }

    /** Returns how many words are inserted in the unit and the units in it. */
    int inserted() {
      var own = difference == null ? 0 : difference.inserted();
      return own + units.stream().mapToInt(Node::inserted).sum();
    }

    /** Returns how many words are deleted in the unit and the units in it. */
    int deleted() {
      var own = difference == null ? 0 : difference.deleted();
      return own + units.stream().mapToInt(Node::deleted).sum();
    }

    /**
     * Returns the unit as it shows in a unit that shows whole. An inserted or deleted unit is
     * marked whole. Any other unit is marked where it and the units in it differ.
     */
    Element within() {
      return difference != null && difference.kind() != Kind.CHANGED
          ? difference.shown()
          : Content.of(this, true);
    }
  }

  /**
   * Compares the units of two texts.
   *
   * @param before the units of the earlier text
   * @param after the units of the later text
   * @param pairs which unit of the later text is which unit of the earlier text
   */
  private record Compare(Units before, Units after, Pairing pairs) {

    /**
     * Returns the units in the unit {@code is} of the later text, compared with the units in the
     * unit {@code was} that it pairs with. If both are null, returns the outermost units.
     *
     * <p>The units are in the order of the later text. Each deleted unit comes after the later unit
     * that pairs with the last paired unit before it, or first if there is no such unit. A unit
     * that moved out of {@code was} is not deleted: it shows where the later text has it.
     */
    List<Node> units(@Nullable Unit was, @Nullable Unit is) {
      var later = in(after, is);
      // The deleted units that stood first, then those that stood after each later unit.
      var deleted = new ArrayList<List<Node>>();
      for (var j = 0; j <= later.size(); j++) {
        deleted.add(new ArrayList<>());
      }
      var place = 0;
      for (var unit : in(before, was)) {
        var now = pairs.is(unit);
        if (now == null) {
          deleted.get(place).add(deleted(unit, is));
        } else if (later.contains(now)) {
          place = later.indexOf(now) + 1;
        }
      }
      var nodes = new ArrayList<Node>(deleted.get(0));
      for (var j = 0; j < later.size(); j++) {
        var unit = later.get(j);
        var earlier = pairs.was(unit);
        nodes.add(earlier == null ? inserted(unit) : paired(earlier, unit));
        nodes.addAll(deleted.get(j + 1));
      }
      return nodes;
    }

    /**
     * Returns the unit {@code unit} of only the earlier text. It was in the later unit {@code in}.
     */
    private Node deleted(Unit unit, @Nullable Unit in) {
      var difference =
          whole(unit, before, Kind.DELETED, in == null ? null : in.eId())
              .earlierMarkers(Markers.of(unit, before))
              .markers(Markers.deleted(unit, after, in));
      return Node.of(unit, true, difference, null, List.of());
    }

    /** Returns the unit {@code unit} of only the later text. */
    private Node inserted(Unit unit) {
      var difference =
          whole(unit, after, Kind.INSERTED, unit.parent()).markers(Markers.of(unit, after));
      return Node.of(unit, false, difference, null, List.of());
    }

    /**
     * Returns the unit {@code is} of the later text, compared with the unit {@code was} of the
     * earlier text that it pairs with. The unit is changed if its own blocks or notes differ.
     */
    private Node paired(Unit was, Unit is) {
      var marking = Marking.of(was, is);
      var units = units(was, is);
      if (marking == null) {
        return Node.of(is, false, null, null, units);
      }
      var difference =
          DifferenceBuilder.builder()
              .eId(is.eId())
              .kind(Kind.CHANGED)
              .unit(sibling(is))
              .inserted(marking.blocks().inserted())
              .deleted(marking.blocks().deleted())
              .parent(is.parent())
              .list(is.list())
              .section(is.section())
              .markers(Markers.of(is, after))
              .earlierMarkers(Markers.of(was, before));
      var node = Node.of(is, false, difference, marking, units);
      difference.shown(Content.of(node, false));
      return node;
    }

    /**
     * Returns {@code unit} of {@code units} as a whole difference, with every word in it marked
     * inserted or deleted, by its {@code kind}. The unit is in the unit {@code parent} of the later
     * text.
     */
    private static DifferenceBuilder whole(
        Unit unit, Units units, Kind kind, @Nullable String parent) {
      var mark = kind == Kind.INSERTED ? Marks.INSERTED : Marks.DELETED;
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
          .parent(parent)
          .list(unit.list())
          .section(unit.section())
          .markers(List.of())
          .earlierMarkers(List.of());
    }

    /** Returns the units directly in {@code unit} of {@code units}, or its outermost units. */
    private static List<Unit> in(Units units, @Nullable Unit unit) {
      var found = new ArrayList<Unit>();
      for (var eId : units.in(unit == null ? null : unit.eId())) {
        var each = units.get(eId);
        if (each != null) {
          found.add(each);
        }
      }
      return found;
    }
  }

  /**
   * How the own blocks and notes of a unit differ from those of the unit that it was.
   *
   * @param blocks its own blocks as they show: a changed block marked word by word, an inserted
   *     block marked whole, and each deleted block marked whole, where it stood. It counts the
   *     marked words of the blocks and of the notes.
   * @param notes its notes, marked against the notes that it had, with the deleted notes where they
   *     stood. Null if the notes say the same.
   */
  private record Marking(Parts.Shown blocks, @Nullable Element notes) {

    /**
     * Returns how {@code to} differs from {@code from}. Returns null if the two units say the same.
     */
    static @Nullable Marking of(Unit from, Unit to) {
      // What the two units say is worked out from their own blocks and notes alone. Most units of
      // two versions have the same ones, which say the same without working it out.
      if ((from.blocks().equals(to.blocks()) && from.notes().equals(to.notes()))
          || numbered(from).equals(numbered(to))) {
        return null;
      }
      var blocks = new Parts.Shown();
      for (var each : Parts.differing(from.blocks(), to.blocks(), from.keys(), to.keys())) {
        switch (each) {
          case Parts.Changed(int at, Element was, Element is) ->
              blocks.replace(at, Marks.between(was, is));
          case Parts.Inserted(int at, Element is) ->
              blocks.replace(at, Marks.all(is, Marks.INSERTED));
          case Parts.Deleted(int at, Element was) ->
              blocks.delete(at, Marks.all(was, Marks.DELETED));
        }
      }
      @Nullable Element notes = null;
      if (!keys(from.notes()).equals(keys(to.notes()))) {
        var marked = Marks.between(Differ.notes(from.notes()), Differ.notes(to.notes()));
        notes = marked.element();
        blocks.count(marked);
      }
      return new Marking(blocks, notes);
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

    /** Returns what each of {@code notes} says, as a comparison matches it. */
    private static List<String> keys(List<Element> notes) {
      return notes.stream().map(Wording::key).toList();
    }
  }

  /**
   * The content of a unit of the later text as a difference shows it, in the order of the text.
   *
   * <ul>
   *   <li>Its own blocks show as its {@link Marking} has them. The blocks deleted after its last
   *       block come right after that block.
   *   <li>A changed unit that does not show whole has only its own content: the units in it differ
   *       on their own. A container with only units in it is then left out.
   *   <li>A unit that shows whole has the units in it too, each as {@link Node#within} shows it. A
   *       deleted unit shows beside a unit that it stood beside. See {@link #place}.
   *   <li>What a consolidation keeps of a deletion is left out. See {@link Units}.
   * </ul>
   */
  private static final class Content {
    private final boolean withUnits;
    private final Parts.Shown blocks;
    private final int count;
    private final Map<String, Node> units = new HashMap<>();
    private final Map<String, List<Node>> deletedBefore = new HashMap<>();
    private final Map<String, List<Node>> deletedAfter = new HashMap<>();
    private final List<Node> deletedAlone = new ArrayList<>();
    private int next;

    private Content(Node node, boolean withUnits) {
      var marking = node.marking();
      this.withUnits = withUnits;
      this.blocks = marking == null ? new Parts.Shown() : marking.blocks();
      this.count = node.unit().blocks().size();
      place(node.units());
    }

    /**
     * Places each deleted unit of {@code nodes} beside a unit of the later text: before the next
     * such unit, or after the last one if there is no next. Thus a deleted item shows in a list.
     */
    private void place(List<Node> nodes) {
      var waiting = new ArrayList<Node>();
      @Nullable String last = null;
      for (var each : nodes) {
        if (each.earlier()) {
          waiting.add(each);
          continue;
        }
        last = each.unit().eId();
        units.put(last, each);
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

    /** Returns the unit of {@code node} as it shows, with the units in it if {@code withUnits}. */
    static Element of(Node node, boolean withUnits) {
      var content = new Content(node, withUnits);
      var element = node.unit().element();
      // A unit with no blocks left shows its deleted blocks first.
      var children = new ArrayList<>(content.count == 0 ? content.blocks.before(0) : List.of());
      children.addAll(content.in(element));
      if (withUnits) {
        // The later unit has no units left. Its deleted units go after its blocks.
        children.addAll(listed(content.deletedAlone));
      }
      var marking = node.marking();
      if (marking != null && marking.notes() != null) {
        children.add(marking.notes());
      }
      return copy(element, children);
    }

    private List<Element> in(Element element) {
      var out = new ArrayList<Element>();
      for (var child : element.children()) {
        var eId = Units.eId(child);
        if (child.name().equals("deleted")) {
          continue;
        }
        if (eId != null) {
          if (withUnits) {
            out.addAll(unit(eId, child));
          }
        } else if (child.name().equals("provenance") || child.name().equals("notes")) {
          // The notes at the end of an annex are not its blocks. See Units.
          out.add(child);
        } else if (Units.holdsUnits(child)) {
          var inside = in(child);
          if (!inside.isEmpty()) {
            out.add(copy(child, inside));
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

    /** Returns the unit {@code eId} in it, with the deleted units that are placed beside it. */
    private List<Element> unit(String eId, Element element) {
      var out = new ArrayList<Element>();
      deletedBefore.getOrDefault(eId, List.of()).forEach(deleted -> out.add(deleted.within()));
      var node = units.get(eId);
      out.add(node == null ? element : node.within());
      deletedAfter.getOrDefault(eId, List.of()).forEach(deleted -> out.add(deleted.within()));
      return out;
    }

    /**
     * Returns the deleted units of {@code nodes} as they show, each in the list that it was an item
     * of. Units of one list go in one list.
     */
    private static List<Element> listed(List<Node> nodes) {
      var out = new ArrayList<Element>();
      for (var node : nodes) {
        var list = node.unit().list();
        var last = out.isEmpty() ? null : out.getLast();
        if (list == null) {
          out.add(node.within());
        } else if (last != null
            && last.name().equals(list.name())
            && last.attributes().equals(list.attributes())) {
          var items = new ArrayList<>(last.children());
          items.add(node.within());
          out.set(out.size() - 1, copy(last, items));
        } else {
          out.add(copy(list, List.of(node.within())));
        }
      }
      return out;
    }
  }

  /**
   * Shows the compared units as the differences of the later text, in order, each with what the
   * later text has before it.
   */
  private static final class Show {
    private final List<Difference> placed = new ArrayList<>();
    private List<Context> context = new ArrayList<>();

    /**
     * Places {@code nodes}, the units in the unit {@code in} of the later text, in order. {@code
     * in} is null for the outermost units.
     */
    void units(List<Node> nodes, @Nullable Unit in) {
      // The run of units that are the same in both texts, which waits to be folded.
      var same = new ArrayList<Sibling>();
      for (var node : nodes) {
        var unit = node.unit();
        var difference = node.difference();
        if (!node.differs()) {
          same.add(sibling(unit));
          continue;
        }
        fold(same, in);
        if (difference == null) {
          context.add(new Context(List.of(sibling(unit)), unit.parent(), lead(unit)));
          units(node.units(), unit);
          continue;
        }
        var whole = node.showsWhole();
        if (whole) {
          var inserted = node.inserted();
          var deleted = node.deleted();
          difference.shown(Content.of(node, true)).inserted(inserted).deleted(deleted);
        }
        placed.add(difference.lead(in == null ? null : lead(in)).context(context).build());
        context = new ArrayList<>();
        if (!whole) {
          units(node.units(), unit);
        }
      }
      fold(same, in);
    }

    /** Folds the run {@code same} into the context and empties it. */
    private void fold(List<Sibling> same, @Nullable Unit in) {
      if (!same.isEmpty()) {
        context.add(new Context(same, in == null ? null : in.eId(), null));
        same.clear();
      }
    }
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
   * Returns the opening of {@code unit}: its num, heading and subheading, and its first other
   * block.
   */
  private static Element lead(Unit unit) {
    var opening = new ArrayList<Element>();
    for (var block : unit.blocks()) {
      opening.add(block);
      if (!TITLES.contains(block.name())) {
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
}
