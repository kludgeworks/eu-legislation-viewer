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
 * Cuts a text into windows, the segments that the page of a reader loads. The segments are an index
 * on the tree, not part of it. Thus how texts are cut can change without a change to what they are.
 *
 * <ul>
 *   <li>If a container is heavier than {@link #TARGET}, its children are cut into runs. A run holds
 *       leaves and containers lighter than the target, for example an article or a list.
 *   <li>A child heavier than the target is cut in the same way inside.
 *   <li>A child ends a run with a chance of its weight divided by the target. Its hash decides, as
 *       in content-defined chunking, for example the prolly trees of Dolt. The cuts move with the
 *       content, not with what comes before it. Thus a change rewrites only the run that it falls
 *       in.
 *   <li>A run never ends inside the rowspan of a table row.
 *   <li>A text that is not heavier than the target has no segments. It loads whole.
 * </ul>
 */
public final class Segmentation {

  /** The average weight of a segment. This is a few screens of text. */
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
    var open = 0; // The rows that a rowspan still covers.
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

  /**
   * Returns whether a run ends after this node. The chance is the weight of the node divided by the
   * target.
   */
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
