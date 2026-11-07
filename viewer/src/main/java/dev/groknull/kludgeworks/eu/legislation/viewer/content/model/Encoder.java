// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Edge;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Overlay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import org.jspecify.annotations.Nullable;

/**
 * Encodes a legal text as a tree of nodes, from the bottom up. Each node is known by its hash,
 * which {@link Identity} makes.
 *
 * <ul>
 *   <li>A block, as {@link LegalText} defines it, is a leaf. The leaf is the JSON of the block,
 *       made by {@link JsonMl}. {@link Annotations} takes its eIds out. {@link NoteLinks} cites the
 *       notes of the block by their hashes.
 *   <li>Any other element is a container, {@code {"n": name, "a": {attributes}, "c": [[child,
 *       overlay]…]}}. Its eId is also taken out. Its children are cited by their hashes.
 * </ul>
 *
 * <p>What is taken out of a node is the overlay on the edge to it. The hash of the overlay of the
 * edge is part of the content of the container. Thus the hash of a root fixes everything below it,
 * the identifiers too. The tree holds each node once, however often the text has it. The weight of
 * a node is the number of bytes of the JSON of its leaves. It tells how much reading the node
 * holds.
 */
public final class Encoder {

  private record Built(Node node, @Nullable ContentHash overlay) {}

  private final NoteLinks links;
  private final Map<ContentHash, Node> nodes = new LinkedHashMap<>();
  private final Map<ContentHash, Overlay> overlays = new LinkedHashMap<>();

  private Encoder(NoteLinks links) {
    this.links = links;
  }

  /**
   * @param legalText a legal-text document, or its element
   */
  public static Tree encode(XdmNode legalText) {
    var root = legalText;
    if (root.getNodeKind() == XdmNodeKind.DOCUMENT) {
      root = LegalText.elements(root).getFirst();
    }
    if (!LegalText.isRoot(root)) {
      throw new IllegalArgumentException("Not legal-text: " + root.getNodeName().getClarkName());
    }
    var encoder = new Encoder(NoteLinks.of(root));
    var built = encoder.build(root);
    if (built.overlay() != null) {
      // A root is one hash, so it cannot have an overlay.
      throw new IllegalArgumentException("A legalText with an eId");
    }
    return new Tree(built.node().hash(), encoder.nodes, encoder.overlays);
  }

  private Built build(XdmNode el) {
    if (LegalText.isLeaf(el)) {
      var json = JsonMl.of(el);
      var facts = Annotations.lift(json);
      facts.addAll(links.link(json));
      var bytes = Identity.bytes(json);
      var node = new Node(Identity.of(bytes), Kind.LEAF, bytes, bytes.length, List.of());
      return new Built(add(node), overlay(facts));
    }
    var name = LegalText.elementName(el.getNodeName());
    var attrs = JsonMl.attributes(el);
    var facts = Annotations.lift(attrs);
    var c = new ArrayList<Object>();
    var edges = new ArrayList<Edge>();
    var weight = 0L;
    for (var child : LegalText.elements(el)) {
      var built = build(child);
      var overlay = built.overlay();
      c.add(Arrays.asList(built.node().hash().hex(), overlay == null ? null : overlay.hex()));
      edges.add(new Edge(built.node().hash(), overlay));
      weight += built.node().weight();
    }
    var hash = Identity.of(Map.of("n", name, "a", attrs, "c", c));
    var content = Identity.bytes(Map.of("n", name, "a", attrs));
    return new Built(
        add(new Node(hash, Kind.CONTAINER, content, weight, List.copyOf(edges))), overlay(facts));
  }

  private Node add(Node node) {
    nodes.putIfAbsent(node.hash(), node);
    return node;
  }

  private @Nullable ContentHash overlay(List<Object> facts) {
    if (facts.isEmpty()) {
      return null;
    }
    var bytes = Identity.bytes(facts);
    var hash = Identity.of(bytes);
    overlays.putIfAbsent(hash, new Overlay(hash, bytes));
    return hash;
  }
}
