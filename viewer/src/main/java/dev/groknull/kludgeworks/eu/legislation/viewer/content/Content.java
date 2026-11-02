// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.List;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import net.sf.saxon.s9api.XdmNode;

/** The store of legal texts: each version's legal-text XML as a root of the shared tree. */
public interface Content {

  /**
   * Stores a legal text, writing only the nodes not stored yet; storing the same text again writes
   * nothing.
   *
   * @param legalText the converter's output: a document whose element is legalText
   * @return its root
   */
  ContentHash store(XdmNode legalText);

  /**
   * The segments of the text at {@code root}, in order; none for a text short enough to load whole,
   * or one not stored.
   */
  List<Segment> segments(ContentHash root);

  /**
   * The legal text at {@code root}, its element legalText, as it was stored; none if none is stored
   * there.
   */
  Optional<Element> read(ContentHash root);

  /**
   * Writes the legal text at {@code root} to {@code out} as it was stored, but for the whitespace
   * between elements, which legal-text doesn't hold (its text is only ever in elements of its own).
   *
   * @return whether a text is stored there: if not, nothing is written
   */
  boolean rebuild(ContentHash root, XMLStreamWriter out) throws XMLStreamException;
}
