// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Segment;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A text's windows, the segments a reader's page loads, as an index on its tree (not part of it, so
 * how texts are cut can change without changing what they are). A container heavier than the {@link
 * #TARGET} has its children cut into runs: of leaves and of containers lighter than the target (an
 * article, a list); a heavier child is cut the same way inside. A child ends a run with a chance of
 * its weight in the target, decided by its hash, as content-defined chunking does (Dolt's prolly
 * trees): the cuts move with the content, not with what comes before it, so a change rewrites only
 * the run it falls in. Never inside a table row's rowspan. A text no heavier than the target has no
 * segments: it loads whole.
 */
public final class Segmentation {

  /** A segment's weight on average: a few screens of text. */
  static final long TARGET = 64 * 1024;

  private static final byte[] ROW = "[\"tr\",".getBytes(StandardCharsets.UTF_8);

  private final Tree tree;
  private final List<Segment> segments = new ArrayList<>();

  private Segmentation(Tree tree) {
    this.tree = tree;
  }

  public static List<Segment> of(Tree tree) {
    var segmentation = new Segmentation(tree);
    segmentation.cut(segmentation.node(tree.root()), 0);
    return List.copyOf(segmentation.segments);
  }

  private void cut(Node container, long offset) {
    if (container.kind() != Kind.CONTAINER || container.weight() <= TARGET) {
      return;
    }
    var children = container.children();
    var from = 0;
    var start = offset;
    var at = offset;
    var open = 0; // rows a rowspan still covers
    for (var i = 0; i < children.size(); i++) {
      var child = node(children.get(i).child());
      if (child.kind() == Kind.CONTAINER && child.weight() > TARGET) {
        close(container, from, i, start, at);
        cut(child, at);
        at += child.weight();
        from = i + 1;
        start = at;
        continue;
      }
      at += child.weight();
      var rowspan = rowspan(child);
      if (rowspan > 0) {
        open = Math.max(open - 1, rowspan - 1);
      }
      if (open == 0 && ends(child)) {
        close(container, from, i + 1, start, at);
        from = i + 1;
        start = at;
      }
    }
    close(container, from, children.size(), start, at);
  }

  private void close(Node container, int from, int to, long start, long end) {
    if (from == to) {
      return;
    }
    var run = new ArrayList<Object>();
    for (var edge : container.children().subList(from, to)) {
      var overlay = edge.overlay();
      run.add(Arrays.asList(edge.child().hex(), overlay == null ? null : overlay.hex()));
    }
    segments.add(
        new Segment(
            segments.size(), Identity.of(run), container.hash(), from, to, start, end - start));
  }

  /** Whether a run ends after this node: with a chance of its weight in the target. */
  private static boolean ends(Node node) {
    var chance = Long.parseLong(node.hash().hex().substring(0, 8), 16);
    return chance * TARGET < node.weight() << 32;
  }

  private static int rowspan(Node node) {
    var content = node.content();
    if (node.kind() != Kind.LEAF
        || !Arrays.equals(content, 0, Math.min(ROW.length, content.length), ROW, 0, ROW.length)) {
      return 0;
    }
    return LegalText.rowspan((List<?>) Identity.parse(content));
  }

  private Node node(ContentHash hash) {
    return tree.node(hash);
  }
}
