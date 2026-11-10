// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Edge;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cuts the tree of a text into chunks of about {@link #LIMIT} bytes of JSON.
 *
 * <p>A chunk is a JSON array of nodes, a run of the children of one container. A node is one of:
 *
 * <ul>
 *   <li>{@code ["l", facts, block]}: a leaf, the JSON of its block as {@link Encoder} made it.
 *   <li>{@code ["c", facts, {"a": attributes, "n": name}, [children…]]}: a container.
 *   <li>{@code ["r", hash]}: among the children of a container, the chunk that holds the next run
 *       of them.
 * </ul>
 *
 * <p>The facts are the overlay on the edge to the node (its eIds and note hrefs), or null.
 *
 * <ul>
 *   <li>A node goes in its chunk whole, with everything under it, if that is no more than {@link
 *       #LIMIT}.
 *   <li>A larger container keeps only its own content in the chunk. Its children are cut into runs,
 *       and each run is a chunk of its own. A child larger than the limit is a run on its own.
 *   <li>A run ends at a child whose hash ends a run, as in content-defined chunking, once the run
 *       holds a quarter of the limit, or when it holds the limit. The cuts move with the content,
 *       not with what comes before it. Thus a change rewrites the run that it falls in and the
 *       chunks above it, not the runs beside it.
 * </ul>
 *
 * <p>The chunks are an index on the tree, not part of it. The root of a text is the hash of its
 * tree, so how it is cut can change without a change to what it is.
 */
public final class Chunker {

  /** The size of a chunk, in bytes of JSON. Benchmarked on 2005/396: smaller reads of an annex. */
  static final int LIMIT = 16 * 1024;

  static final String LEAF = "l";
  static final String CONTAINER = "c";
  static final String RUN = "r";

  private static final byte[] NULL = "null".getBytes(StandardCharsets.UTF_8);

  /** About what the JSON of a node adds to its content and facts. */
  private static final int OVERHEAD = 16;

  private final Tree tree;
  private final Map<ContentHash, Long> sizes = new HashMap<>();
  private final Map<ContentHash, byte[]> bodies = new LinkedHashMap<>();

  private Chunker(Tree tree) {
    this.tree = tree;
  }

  public static Chunks of(Tree tree) {
    var chunker = new Chunker(tree);
    var top = chunker.run(List.of(new Edge(tree.root(), null)));
    return new Chunks(top, chunker.bodies);
  }

  private ContentHash run(List<Edge> edges) {
    var out = new ByteArrayOutputStream();
    out.write('[');
    for (var i = 0; i < edges.size(); i++) {
      if (i > 0) {
        out.write(',');
      }
      node(edges.get(i), out);
    }
    out.write(']');
    var body = out.toByteArray();
    var hash = Identity.of(body);
    bodies.putIfAbsent(hash, body);
    return hash;
  }

  private void node(Edge edge, ByteArrayOutputStream out) {
    var node = tree.node(edge.child());
    out.write('[');
    string(node.kind() == Kind.LEAF ? LEAF : CONTAINER, out);
    out.write(',');
    out.writeBytes(facts(edge));
    out.write(',');
    out.writeBytes(node.content());
    if (node.kind() == Kind.CONTAINER) {
      out.write(',');
      out.write('[');
      if (size(node) <= LIMIT) {
        var children = node.children();
        for (var i = 0; i < children.size(); i++) {
          if (i > 0) {
            out.write(',');
          }
          node(children.get(i), out);
        }
      } else {
        var runs = runs(node.children());
        for (var i = 0; i < runs.size(); i++) {
          if (i > 0) {
            out.write(',');
          }
          out.write('[');
          string(RUN, out);
          out.write(',');
          string(runs.get(i).hex(), out);
          out.write(']');
        }
      }
      out.write(']');
    }
    out.write(']');
  }

  /** Cuts the children of a container into runs, and returns the chunks that hold them. */
  private List<ContentHash> runs(List<Edge> children) {
    var runs = new ArrayList<ContentHash>();
    var run = new ArrayList<Edge>();
    var size = 0L;
    for (var edge : children) {
      var child = tree.node(edge.child());
      var weight = size(child) + facts(edge).length;
      if (weight > LIMIT) {
        if (!run.isEmpty()) {
          runs.add(run(run));
          run = new ArrayList<>();
          size = 0;
        }
        runs.add(run(List.of(edge)));
        continue;
      }
      run.add(edge);
      size += weight;
      if (size >= LIMIT || (ends(child) && size >= LIMIT / 4)) {
        runs.add(run(run));
        run = new ArrayList<>();
        size = 0;
      }
    }
    if (!run.isEmpty()) {
      runs.add(run(run));
    }
    return runs;
  }

  /** Returns whether a run ends after this node: for one node in eight, by its hash. */
  private static boolean ends(Node node) {
    return (node.hash().bytes()[0] & 0x07) == 0;
  }

  /** Returns about how many bytes of JSON the node holds, with everything under it. */
  private long size(Node node) {
    var known = sizes.get(node.hash());
    if (known != null) {
      return known;
    }
    long size = node.content().length + OVERHEAD;
    for (var edge : node.children()) {
      size += size(tree.node(edge.child())) + facts(edge).length;
    }
    sizes.put(node.hash(), size);
    return size;
  }

  private byte[] facts(Edge edge) {
    var overlay = edge.overlay();
    if (overlay == null) {
      return NULL;
    }
    return Objects.requireNonNull(tree.overlays().get(overlay), overlay::hex).facts();
  }

  private static void string(String value, ByteArrayOutputStream out) {
    out.write('"');
    out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    out.write('"');
  }
}
