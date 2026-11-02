// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jspecify.annotations.Nullable;

/**
 * A legal text rebuilt from its tree, the Encoder's steps undone: each block's notes cited as
 * before (NoteLinks), its eIds put back (Annotations), written as XML (JsonMl). The notes are read
 * in a first pass, since a reference may come before the note it cites.
 */
public final class Decoder {
  private final Tree tree;
  private final NoteLinks.Targets notes = new NoteLinks.Targets();

  private Decoder(Tree tree) {
    this.tree = tree;
  }

  public static void write(Tree tree, XMLStreamWriter out) throws XMLStreamException {
    var decoder = new Decoder(tree);
    var root = decoder.node(tree.root());
    decoder.readNotes(root, null);
    out.writeStartDocument();
    decoder.write(root, null, out, true);
    out.writeEndDocument();
  }

  private void readNotes(Node node, @Nullable ContentHash overlay) {
    if (node.kind() == Kind.LEAF) {
      notes.read((List<?>) Identity.parse(node.content()), facts(overlay));
    } else {
      node.children().forEach(e -> readNotes(node(e.child()), e.overlay()));
    }
  }

  private void write(Node node, @Nullable ContentHash overlay, XMLStreamWriter out, boolean root)
      throws XMLStreamException {
    var facts = facts(overlay);
    if (node.kind() == Kind.LEAF) {
      var leaf = (List<?>) Identity.parse(node.content());
      notes.unlink(leaf, facts);
      Annotations.apply(leaf, facts);
      JsonMl.write(leaf, out);
      return;
    }
    var content = (Map<?, ?>) Identity.parse(node.content());
    @SuppressWarnings("unchecked")
    var attrs = (Map<String, Object>) Objects.requireNonNull(content.get("a"));
    Annotations.apply(attrs, facts);
    JsonMl.startElement((String) Objects.requireNonNull(content.get("n")), out);
    if (root) {
      JsonMl.declareNamespaces(out);
    }
    for (var a : attrs.entrySet()) {
      JsonMl.attribute(a.getKey(), (String) a.getValue(), out);
    }
    for (var e : node.children()) {
      write(node(e.child()), e.overlay(), out, false);
    }
    out.writeEndElement();
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
