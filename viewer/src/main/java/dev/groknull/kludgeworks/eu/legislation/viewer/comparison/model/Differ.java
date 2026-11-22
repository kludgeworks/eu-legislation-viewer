// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Finds how one text differs from another, as the Differences view shows it. It works in four
 * stages:
 *
 * <ol>
 *   <li>{@link Occurrences} reads the occurrences of each text.
 *   <li>{@link Pairing} pairs the occurrences of the two texts: siblings with siblings, and an
 *       occurrence that moved into the occurrence before it, or out of it.
 *   <li>The occurrences are compared, from the outermost occurrences inwards. An occurrence in only
 *       one text is inserted or deleted, with its occurrences. An occurrence in both texts whose
 *       own wording differs is changed, and the occurrences in it are compared in turn.
 *   <li>The differences are shown in the order of the later text, each with what the later text has
 *       before it.
 * </ol>
 *
 * <p>The occurrences of the two texts are in one order: the occurrences of the later text in their
 * order, and each deleted occurrence after the occurrence that it stood after. Thus a deleted
 * occurrence comes before the occurrences inserted in its place.
 *
 * <p>The footnotes that an occurrence refers to are compared as part of it. A changed occurrence
 * shows its footnotes under it, with their edits, if they differ. An inserted or deleted occurrence
 * shows its footnotes, whole, as insertions or deletions. A footnote that only one text has, and
 * each reference to it, says so ({@link Edits#ONLY}).
 *
 * <p>The own blocks of a changed occurrence are matched by what they say ({@link Pieces}). {@link
 * Edits} shows the edits of a changed block word by word.
 *
 * <p>Each occurrence of the later text is placed once, as one of these:
 *
 * <ul>
 *   <li>a difference
 *   <li>the opening of an occurrence that holds differences
 *   <li>an occurrence that is the same in both texts, with its occurrences. It is folded in a run
 *       of such occurrences in the same occurrence.
 * </ul>
 *
 * <p>These rules decide what counts as one difference:
 *
 * <ul>
 *   <li>A changed occurrence and the occurrences in it that differ are one difference if the same
 *       acts made all of them. The difference shows whole, and each occurrence shows its edits
 *       where it differs. For example, 11.1.3 with its points (a) to (d), all from 2019/103.
 *   <li>If the num of an occurrence only gained or lost its full stop, no act changed it. Thus the
 *       occurrence does not differ. For example, "11.3" to "11.3.".
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
   * deleted occurrence is where it stood, before the occurrences inserted in its place.
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
    var before = Occurrences.of(withoutPreamble == Side.LATER ? withoutPreamble(from) : from);
    var after = Occurrences.of(withoutPreamble == Side.EARLIER ? withoutPreamble(to) : to);
    var show = new Show();
    show.occurrences(
        new Compare(before, after, Pairing.of(before, after)).occurrences(null, null), null);
    return new Compared(show.placed, show.context, withoutPreamble);
  }

  /**
   * Returns whether {@code text} has a preamble with occurrences in it. The occurrences are its
   * citations and its recitals. Legal-text has the preamble only in the text itself, between its
   * preface and its body, so the search stops there.
   */
  private static boolean preamble(Element text) {
    if (text.name().equals("preamble")) {
      return Occurrences.holdsOccurrences(text);
    }
    for (var child : text.children()) {
      if (child.name().equals("preamble") && Occurrences.holdsOccurrences(child)) {
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
   * An occurrence of the two texts, compared.
   *
   * @param occurrence the occurrence, as the later text has it. For a deleted occurrence, as the
   *     earlier text has it.
   * @param earlier whether the occurrence is of only the earlier text: it is deleted
   * @param difference how the occurrence differs. Null if its own wording is the same in both
   *     texts.
   * @param editing how its own blocks and footnotes differ. Null if the occurrence is not changed.
   * @param occurrences the occurrences in it, compared, in the one order of the two texts. Empty
   *     for an inserted or deleted occurrence, which is one difference with its occurrences.
   * @param differs whether the occurrence differs, or an occurrence in it does
   */
  private record Node(
      Occurrence occurrence,
      boolean earlier,
      @Nullable DifferenceBuilder difference,
      @Nullable Editing editing,
      List<Node> occurrences,
      boolean differs) {

    static Node of(
        Occurrence occurrence,
        boolean earlier,
        @Nullable DifferenceBuilder difference,
        @Nullable Editing editing,
        List<Node> occurrences) {
      var differs = difference != null || occurrences.stream().anyMatch(Node::differs);
      return new Node(occurrence, earlier, difference, editing, occurrences, differs);
    }

    /**
     * Returns whether the occurrence shows whole, as one difference with the occurrences in it that
     * differ. It does if it is changed, and the acts that changed it also made every difference in
     * it.
     */
    boolean showsWhole() {
      return difference != null
          && difference.kind() == Kind.CHANGED
          && occurrences.stream().anyMatch(Node::differs)
          && madeBy(acts(difference));
    }

    /** Returns whether {@code acts} made every difference in the occurrence. */
    private boolean madeBy(Set<String> acts) {
      for (var each : occurrences) {
        var in = each.difference();
        if ((in != null && !acts(in).equals(acts)) || !each.madeBy(acts)) {
          return false;
        }
      }
      return true;
    }

    /** Returns how many words are inserted in the occurrence and the occurrences in it. */
    int inserted() {
      var own = difference == null ? 0 : difference.inserted();
      return own + occurrences.stream().mapToInt(Node::inserted).sum();
    }

    /** Returns how many words are deleted in the occurrence and the occurrences in it. */
    int deleted() {
      var own = difference == null ? 0 : difference.deleted();
      return own + occurrences.stream().mapToInt(Node::deleted).sum();
    }

    /**
     * Returns the occurrence as it shows in an occurrence that shows whole. An inserted or deleted
     * occurrence shows whole, as an insertion or a deletion. Any other occurrence shows its edits
     * where it and the occurrences in it differ.
     */
    Element within() {
      return difference != null && difference.kind() != Kind.CHANGED
          ? difference.shown()
          : Content.of(this, true);
    }
  }

  /**
   * Compares the occurrences of two texts.
   *
   * @param before the occurrences of the earlier text
   * @param after the occurrences of the later text
   * @param pairs which occurrence of the later text is which occurrence of the earlier text
   */
  private record Compare(Occurrences before, Occurrences after, Pairing pairs) {

    /**
     * Returns the occurrences in the occurrence {@code is} of the later text, compared with the
     * occurrences in the occurrence {@code was} that it pairs with. If both are null, returns the
     * outermost occurrences.
     *
     * <p>The occurrences are in the order of the later text. Each deleted occurrence comes after
     * the later occurrence that pairs with the last paired occurrence before it, or first if there
     * is no such occurrence. An occurrence that moved out of {@code was} is not deleted: it shows
     * where the later text has it.
     */
    List<Node> occurrences(@Nullable Occurrence was, @Nullable Occurrence is) {
      var later = in(after, is);
      // The deleted occurrences that stood first, then those that stood after each later
      // occurrence.
      var deleted = new ArrayList<List<Node>>();
      for (var j = 0; j <= later.size(); j++) {
        deleted.add(new ArrayList<>());
      }
      var place = 0;
      for (var occurrence : in(before, was)) {
        var now = pairs.is(occurrence);
        if (now == null) {
          deleted.get(place).add(deleted(occurrence, is));
        } else if (later.contains(now)) {
          place = later.indexOf(now) + 1;
        }
      }
      var nodes = new ArrayList<Node>(deleted.get(0));
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
     * occurrence {@code in}.
     */
    private Node deleted(Occurrence occurrence, @Nullable Occurrence in) {
      var difference =
          whole(occurrence, before, Kind.DELETED, in == null ? null : in.eId())
              .earlierTags(Tags.of(occurrence, before))
              .tags(Tags.deleted(occurrence, after, in));
      return Node.of(occurrence, true, difference, null, List.of());
    }

    /** Returns the occurrence {@code occurrence} of only the later text. */
    private Node inserted(Occurrence occurrence) {
      var difference =
          whole(occurrence, after, Kind.INSERTED, occurrence.parent())
              .tags(Tags.of(occurrence, after));
      return Node.of(occurrence, false, difference, null, List.of());
    }

    /**
     * Returns the occurrence {@code is} of the later text, compared with the occurrence {@code was}
     * of the earlier text that it pairs with. The occurrence is changed if its own blocks or
     * footnotes differ.
     */
    private Node paired(Occurrence was, Occurrence is) {
      var editing = Editing.of(was, is);
      var occurrences = occurrences(was, is);
      if (editing == null) {
        return Node.of(is, false, null, null, occurrences);
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
      var node = Node.of(is, false, difference, editing, occurrences);
      difference.shown(Content.of(node, false));
      return node;
    }

    /**
     * Returns {@code occurrence} of {@code occurrences} as a whole difference, with every word in
     * it shown inserted or deleted, by its {@code kind}. The occurrence is in the occurrence {@code
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
        var footnotes = Edits.all(footnotes(referred), edit);
        var children = new ArrayList<>(shown.children());
        children.add(footnotes.element());
        shown = copy(shown, children);
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
     * Returns the occurrences directly in {@code occurrence} of {@code occurrences}, or its
     * outermost occurrences.
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
  }

  /**
   * How the own blocks and footnotes of an occurrence differ from those of the occurrence that it
   * was.
   *
   * @param blocks its own blocks as they show: a changed block with its edits word by word, an
   *     inserted block whole as an insertion, and each deleted block whole as a deletion, where it
   *     stood. It counts the edited words of the blocks and of the footnotes.
   * @param footnotes its footnotes, with their edits against the footnotes that it had, with the
   *     deleted footnotes where they stood. Null if the footnotes say the same.
   */
  private record Editing(Pieces.Shown blocks, @Nullable Element footnotes) {

    /**
     * Returns how {@code to} differs from {@code from}. Returns null if the two occurrences say the
     * same.
     */
    static @Nullable Editing of(Occurrence from, Occurrence to) {
      // What the two occurrences say is worked out from their own blocks and footnotes alone. Most
      // occurrences of
      // two versions have the same ones, which say the same without working it out.
      if ((from.blocks().equals(to.blocks()) && from.footnotes().equals(to.footnotes()))
          || numbered(from).equals(numbered(to))) {
        return null;
      }
      var blocks = new Pieces.Shown();
      for (var each : Pieces.differing(from.blocks(), to.blocks(), from.keys(), to.keys())) {
        switch (each) {
          case Pieces.Changed(int at, Element was, Element is) ->
              blocks.replace(at, Edits.between(was, is));
          case Pieces.Inserted(int at, Element is) ->
              blocks.replace(at, Edits.all(is, Edits.INSERTED));
          case Pieces.Deleted(int at, Element was) ->
              blocks.delete(at, Edits.all(was, Edits.DELETED));
        }
      }
      @Nullable Element footnotes = null;
      if (!keys(from.footnotes()).equals(keys(to.footnotes()))) {
        var edited =
            Edits.between(Differ.footnotes(from.footnotes()), Differ.footnotes(to.footnotes()));
        footnotes = edited.element();
        blocks.count(edited);
      }
      return new Editing(blocks, footnotes);
    }

    /**
     * Returns what the own blocks of {@code occurrence} say, with its num without the full stop
     * after it, and what the footnotes that they refer to say. An occurrence whose footnote an act
     * changed differs. If a num only gained or lost its full stop, the Publications Office typeset
     * it, and no act changed it. For example, "11.3" to "11.3.".
     */
    private static List<String> numbered(Occurrence occurrence) {
      var wording = new ArrayList<String>();
      for (var block : occurrence.blocks()) {
        var said = Wording.key(block);
        wording.add(block.name().equals("num") ? said.replaceFirst("\\.$", "") : said);
      }
      occurrence.footnotes().forEach(footnote -> wording.add("footnote:" + Wording.key(footnote)));
      return wording;
    }

    /** Returns what each of {@code footnotes} says, as a comparison matches it. */
    private static List<String> keys(List<Element> footnotes) {
      return footnotes.stream().map(Wording::key).toList();
    }
  }

  /**
   * The content of an occurrence of the later text as a difference shows it, in the order of the
   * text.
   *
   * <ul>
   *   <li>Its own blocks show as its {@link Editing} has them. The blocks deleted after its last
   *       block come right after that block.
   *   <li>A changed occurrence that does not show whole has only its own content: the occurrences
   *       in it differ on their own. A container with only occurrences in it is then left out.
   *   <li>An occurrence that shows whole has the occurrences in it too, each as {@link Node#within}
   *       shows it. A deleted occurrence shows beside an occurrence that it stood beside. See
   *       {@link #place}.
   *   <li>What a consolidation keeps of a deletion is left out. See {@link Occurrences}.
   * </ul>
   */
  private static final class Content {
    private final boolean withOccurrences;
    private final Pieces.Shown blocks;
    private final int count;
    private final Map<String, Node> occurrences = new HashMap<>();
    private final Map<String, List<Node>> deletedBefore = new HashMap<>();
    private final Map<String, List<Node>> deletedAfter = new HashMap<>();
    private final List<Node> deletedAlone = new ArrayList<>();
    private int next;

    private Content(Node node, boolean withOccurrences) {
      var editing = node.editing();
      this.withOccurrences = withOccurrences;
      this.blocks = editing == null ? new Pieces.Shown() : editing.blocks();
      this.count = node.occurrence().blocks().size();
      place(node.occurrences());
    }

    /**
     * Places each deleted occurrence of {@code nodes} beside an occurrence of the later text:
     * before the next such occurrence, or after the last one if there is no next. Thus a deleted
     * item shows in a list.
     */
    private void place(List<Node> nodes) {
      var waiting = new ArrayList<Node>();
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
    static Element of(Node node, boolean withOccurrences) {
      var content = new Content(node, withOccurrences);
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
      return Edits.referring(copy(element, children));
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
     * Returns the deleted occurrences of {@code nodes} as they show, each in the list that it was
     * an item of. Occurrences of one list go in one list.
     */
    private static List<Element> listed(List<Node> nodes) {
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
          out.set(out.size() - 1, copy(last, items));
        } else {
          out.add(copy(list, List.of(node.within())));
        }
      }
      return out;
    }
  }

  /**
   * Shows the compared occurrences as the differences of the later text, in order, each with what
   * the later text has before it.
   */
  private static final class Show {
    private final List<Difference> placed = new ArrayList<>();
    private List<Context> context = new ArrayList<>();

    /**
     * Places {@code nodes}, the occurrences in the occurrence {@code in} of the later text, in
     * order. {@code in} is null for the outermost occurrences.
     */
    void occurrences(List<Node> nodes, @Nullable Occurrence in) {
      // The run of occurrences that are the same in both texts, which waits to be folded.
      var same = new ArrayList<Sibling>();
      for (var node : nodes) {
        var occurrence = node.occurrence();
        var difference = node.difference();
        if (!node.differs()) {
          same.add(sibling(occurrence));
          continue;
        }
        fold(same, in);
        if (difference == null) {
          context.add(
              new Context(List.of(sibling(occurrence)), occurrence.parent(), lead(occurrence)));
          occurrences(node.occurrences(), occurrence);
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
          occurrences(node.occurrences(), occurrence);
        }
      }
      fold(same, in);
    }

    /** Folds the run {@code same} into the context and empties it. */
    private void fold(List<Sibling> same, @Nullable Occurrence in) {
      if (!same.isEmpty()) {
        context.add(new Context(same, in == null ? null : in.eId(), null));
        same.clear();
      }
    }
  }

  /**
   * Returns the acts that made {@code difference}, from its tags. Returns an empty set if it has no
   * tags.
   */
  private static Set<String> acts(DifferenceBuilder difference) {
    var acts = new HashSet<String>();
    for (var tag : difference.tags()) {
      acts.add(tag.act());
    }
    return acts;
  }

  /**
   * Returns the opening of {@code occurrence}: its num, heading and subheading, and its first other
   * block.
   */
  private static Element lead(Occurrence occurrence) {
    var opening = new ArrayList<Element>();
    for (var block : occurrence.blocks()) {
      opening.add(block);
      if (!TITLES.contains(block.name())) {
        break;
      }
    }
    return copy(occurrence.element(), opening);
  }

  private static Sibling sibling(Occurrence occurrence) {
    var num =
        occurrence.blocks().stream()
            .filter(block -> block.name().equals("num"))
            .findFirst()
            .map(Wording::of)
            .orElse("");
    var kind = occurrence.element().attribute("kind");
    return new Sibling(occurrence.eId(), kind == null ? "" : kind, num);
  }

  private static Element copy(Element element, List<Element> children) {
    return new Element(element.name(), element.attributes(), children, element.text());
  }

  /**
   * Returns {@code footnotes} as the footnotes of an occurrence, under its blocks and occurrences.
   */
  private static Element footnotes(List<Element> footnotes) {
    return new Element("footnotes", Map.of(), footnotes, "");
  }
}
