// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Edge;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A text's outline from its tree, or its skeleton (TreeStore): its units with a rank, in the order
 * a reader meets them, each under the last unit of a higher rank before it, and every container's
 * eId. Their labels and headings are read as plain text, without the notes they cite or the text an
 * amendment deleted.
 */
public final class Outlines {

  /** What a title's text leaves out: note references, deleted text, images, formulas. */
  private static final Set<String> UNREAD = Set.of("noteRef", "deleted", "img", "m:math");

  private final Tree tree;
  private final List<Unit> outermost = new ArrayList<>();
  private final List<String> eIds = new ArrayList<>();
  private final Deque<Unit> open = new ArrayDeque<>();

  private Outlines(Tree tree) {
    this.tree = tree;
  }

  public static Outline of(Tree tree) {
    var outlines = new Outlines(tree);
    outlines.walk(tree.node(tree.root()), null);
    return new Outline(outlines.outermost.stream().map(Unit::entry).toList(), outlines.eIds);
  }

  /** A container and the containers in it, in order; its children a skeleton lacks are passed. */
  private void walk(Node node, @Nullable Edge to) {
    var content = (Map<?, ?>) Identity.parse(node.content());
    @SuppressWarnings("unchecked")
    var attributes = (Map<String, Object>) Objects.requireNonNull(content.get("a"));
    if (to != null) {
      Annotations.apply(attributes, facts(to));
    }
    if (attributes.get("eId") instanceof String eId) {
      eIds.add(eId);
    }
    if ("unit".equals(content.get("n")) && attributes.get("rank") instanceof String rank) {
      add(node, attributes, Integer.parseInt(rank));
    }
    for (var edge : node.children()) {
      var child = tree.nodes().get(edge.child());
      if (child != null && child.kind() == Kind.CONTAINER) {
        walk(child, edge);
      }
    }
  }

  /** A unit with a title, under the last open unit ranked above it. */
  private void add(Node node, Map<String, Object> attributes, int rank) {
    var unit =
        new Unit(
            String.valueOf(attributes.getOrDefault("eId", "")),
            String.valueOf(attributes.get("kind")),
            title(node, "num"),
            title(node, "heading"),
            rank);
    while (!open.isEmpty() && open.peek().rank >= rank) {
      open.pop();
    }
    (open.isEmpty() ? outermost : open.peek().under).add(unit);
    open.push(unit);
  }

  /** The text of the unit's child {@code name} (its num, its heading); empty if it has none. */
  private String title(Node unit, String name) {
    for (var edge : unit.children()) {
      var child = tree.nodes().get(edge.child());
      if (child != null && child.kind() == Kind.LEAF) {
        var leaf = (List<?>) Identity.parse(child.content());
        if (JsonMl.name(leaf).equals(name)) {
          var text = new StringBuilder();
          read(leaf, text);
          return text.toString().replaceAll("\\s+", " ").strip();
        }
      }
    }
    return "";
  }

  private static void read(List<?> el, StringBuilder text) {
    var name = JsonMl.name(el);
    if (UNREAD.contains(name)) {
      return;
    }
    if (name.equals("eol")) {
      text.append(' ');
    } else if (JsonMl.attributes(el).get("v") instanceof String value) {
      text.append(value);
    }
    JsonMl.elements(el).forEach(child -> read(child, text));
  }

  private List<?> facts(Edge edge) {
    var overlay = edge.overlay();
    if (overlay == null) {
      return List.of();
    }
    return (List<?>) Identity.parse(Objects.requireNonNull(tree.overlays().get(overlay)).facts());
  }

  /** A unit in the outline as it is read, the units under it still being added. */
  private static final class Unit {
    final String eId;
    final String kind;
    final String num;
    final String heading;
    final int rank;
    final List<Unit> under = new ArrayList<>();

    Unit(String eId, String kind, String num, String heading, int rank) {
      this.eId = eId;
      this.kind = kind;
      this.num = num;
      this.heading = heading;
      this.rank = rank;
    }

    Outline.Entry entry() {
      return new Outline.Entry(eId, kind, num, heading, under.stream().map(Unit::entry).toList());
    }
  }
}
