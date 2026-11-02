// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A legal text's tree (Encoder): its root, and every node and overlay below it, each once.
 *
 * @param root the text's identity
 */
public record Tree(
    ContentHash root, Map<ContentHash, Node> nodes, Map<ContentHash, Overlay> overlays) {

  public enum Kind {
    LEAF,
    CONTAINER
  }

  /** A child of a container, with the overlay of what was taken out of it, if anything. */
  public record Edge(ContentHash child, @Nullable ContentHash overlay) {}

  /**
   * A node: a leaf's JSON, or a container's name and attributes ({@code {"n", "a"}}), its children
   * beside it; its weight, the bytes of its leaves' JSON.
   */
  public record Node(
      ContentHash hash, Kind kind, byte[] content, long weight, List<Edge> children) {}

  /** The facts taken out of one node, as JSON. */
  public record Overlay(ContentHash hash, byte[] facts) {}

  public Node node(ContentHash hash) {
    return Objects.requireNonNull(nodes.get(hash), hash::hex);
  }
}
