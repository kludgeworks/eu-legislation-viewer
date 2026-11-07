// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The tree of a legal text, which {@link Encoder} makes. It holds its root, and every node and
 * overlay below the root, each once.
 *
 * @param root the identity of the text
 */
public record Tree(
    ContentHash root, Map<ContentHash, Node> nodes, Map<ContentHash, Overlay> overlays) {

  public enum Kind {
    LEAF,
    CONTAINER
  }

  /** A child of a container. If something was taken out of the child, the edge has its overlay. */
  public record Edge(ContentHash child, @Nullable ContentHash overlay) {}

  /**
   * A node. Its content is the JSON of a leaf, or the name and attributes of a container ({@code
   * {"n", "a"}}). The children of a container are kept beside its content. Its weight is the number
   * of bytes of the JSON of its leaves.
   */
  public record Node(
      ContentHash hash, Kind kind, byte[] content, long weight, List<Edge> children) {}

  /** The facts taken out of one node, as JSON. */
  public record Overlay(ContentHash hash, byte[] facts) {}

  public Node node(ContentHash hash) {
    return Objects.requireNonNull(nodes.get(hash), hash::hex);
  }
}
