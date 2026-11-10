// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Rebuilds a legal text from its chunks. It undoes the steps of {@link Encoder} and {@link
 * Chunker}:
 *
 * <ol>
 *   <li>It joins the runs of each container that {@link Chunker} cut.
 *   <li>It cites the notes of each block as before, with {@link NoteLinks}.
 *   <li>It puts back the eIds of each block, with {@link Annotations}.
 *   <li>It makes the JSON of the text, with {@link JsonMl}.
 *   <li>It writes the JSON as XML, or reads it as an {@code Element}.
 * </ol>
 *
 * <p>The notes are read in a first pass, because a reference may come before the note that it
 * cites. Each chunk is parsed once. Putting the eIds and notes back changes the parsed JSON, so a
 * chunk that the text holds twice is parsed again for its second place.
 */
public final class Decoder {
  private final Chunks chunks;
  private final Map<ContentHash, List<?>> parsed = new HashMap<>();
  private final Set<ContentHash> decoded = new HashSet<>();
  private final NoteLinks.Targets notes = new NoteLinks.Targets();

  private Decoder(Chunks chunks) {
    this.chunks = chunks;
  }

  public static void write(Chunks chunks, XMLStreamWriter out) throws XMLStreamException {
    JsonMl.writeDocument(decode(chunks), out);
  }

  /** Writes the text of a tree that {@link Encoder} made, through its chunks. */
  public static void write(Tree tree, XMLStreamWriter out) throws XMLStreamException {
    write(Chunker.of(tree), out);
  }

  /** Returns the element of the text, {@code legalText}, with everything in it. */
  public static Element element(Chunks chunks) {
    return JsonMl.element(decode(chunks));
  }

  /** Returns the element of the text of a tree that {@link Encoder} made, through its chunks. */
  public static Element element(Tree tree) {
    return element(Chunker.of(tree));
  }

  private static List<?> decode(Chunks chunks) {
    var decoder = new Decoder(chunks);
    var root = decoder.run(chunks.top(), false).getFirst();
    decoder.readNotes(root);
    return decoder.decode(decoder.run(chunks.top(), true).getFirst());
  }

  private void readNotes(List<?> node) {
    if (node.getFirst().equals(Chunker.LEAF)) {
      notes.read(block(node), facts(node));
    } else {
      children(node, false).forEach(this::readNotes);
    }
  }

  private List<?> decode(List<?> node) {
    var facts = facts(node);
    if (node.getFirst().equals(Chunker.LEAF)) {
      var leaf = block(node);
      notes.unlink(leaf, facts);
      Annotations.apply(leaf, facts);
      return leaf;
    }
    var content = (Map<?, ?>) node.get(2);
    @SuppressWarnings("unchecked")
    var attrs = (Map<String, Object>) Objects.requireNonNull(content.get("a"));
    Annotations.apply(attrs, facts);
    var el = new ArrayList<Object>(List.of(Objects.requireNonNull(content.get("n")), attrs));
    for (var child : children(node, true)) {
      el.add(decode(child));
    }
    return el;
  }

  /** Returns the children of a container, with its runs joined. */
  private List<List<?>> children(List<?> container, boolean decoding) {
    var children = new ArrayList<List<?>>();
    for (var entry : (List<?>) container.get(3)) {
      var child = (List<?>) entry;
      if (child.getFirst().equals(Chunker.RUN)) {
        children.addAll(run(new ContentHash((String) child.get(1)), decoding));
      } else {
        children.add(child);
      }
    }
    return children;
  }

  /**
   * Returns the nodes of a chunk. Reading notes leaves them as they are, so it shares the parse.
   * Decoding changes them, so it takes the shared parse once and parses the chunk again after.
   */
  @SuppressWarnings("unchecked")
  private List<List<?>> run(ContentHash chunk, boolean decoding) {
    if (decoding && !decoded.add(chunk)) {
      return (List<List<?>>) Identity.parse(chunks.body(chunk));
    }
    return (List<List<?>>)
        parsed.computeIfAbsent(chunk, each -> (List<?>) Identity.parse(chunks.body(each)));
  }

  private static List<?> block(List<?> leaf) {
    return (List<?>) leaf.get(2);
  }

  private static List<?> facts(List<?> node) {
    var facts = node.get(1);
    return facts == null ? List.of() : (List<?>) facts;
  }
}
