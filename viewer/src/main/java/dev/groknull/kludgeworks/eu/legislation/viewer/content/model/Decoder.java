// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jspecify.annotations.Nullable;

/**
 * Rebuilds a legal text from its tree. It undoes the steps of {@link Encoder}:
 *
 * <ol>
 *   <li>It cites the notes of each block as before, with {@link NoteLinks}.
 *   <li>It puts back the eIds of each block, with {@link Annotations}.
 *   <li>It makes the JSON of the text, with {@link JsonMl}.
 *   <li>It writes the JSON as XML, or reads it as an {@code Element}.
 * </ol>
 *
 * <p>The notes are read in a first pass, because a reference may come before the note that it
 * cites.
 */
public final class Decoder {
  private final Tree tree;
  private final NoteLinks.Targets notes = new NoteLinks.Targets();

  private Decoder(Tree tree) {
    this.tree = tree;
  }

  public static void write(Tree tree, XMLStreamWriter out) throws XMLStreamException {
    JsonMl.writeDocument(decode(tree), out);
  }

  /** Returns the element of the text, {@code legalText}, with everything in it. */
  public static Element element(Tree tree) {
    return JsonMl.element(decode(tree));
  }

  private static List<?> decode(Tree tree) {
    var decoder = new Decoder(tree);
    var root = decoder.node(tree.root());
    decoder.readNotes(root, null);
    return decoder.decode(root, null);
  }

  private void readNotes(Node node, @Nullable ContentHash overlay) {
    if (node.kind() == Kind.LEAF) {
      notes.read((List<?>) Identity.parse(node.content()), facts(overlay));
    } else {
      node.children().forEach(e -> readNotes(node(e.child()), e.overlay()));
    }
  }

  private List<?> decode(Node node, @Nullable ContentHash overlay) {
    var facts = facts(overlay);
    if (node.kind() == Kind.LEAF) {
      var leaf = (List<?>) Identity.parse(node.content());
      notes.unlink(leaf, facts);
      Annotations.apply(leaf, facts);
      return leaf;
    }
    var content = (Map<?, ?>) Identity.parse(node.content());
    @SuppressWarnings("unchecked")
    var attrs = (Map<String, Object>) Objects.requireNonNull(content.get("a"));
    Annotations.apply(attrs, facts);
    var el = new ArrayList<Object>(List.of(Objects.requireNonNull(content.get("n")), attrs));
    for (var e : node.children()) {
      el.add(decode(node(e.child()), e.overlay()));
    }
    return el;
  }

  private List<?> facts(@Nullable ContentHash overlay) {
    if (overlay == null) {
      return List.of();
    }
    var stored = Objects.requireNonNull(tree.overlays().get(overlay), overlay::hex);
    return (List<?>) Identity.parse(stored.facts());
  }

  private Node node(ContentHash hash) {
    return tree.node(hash);
  }
}
