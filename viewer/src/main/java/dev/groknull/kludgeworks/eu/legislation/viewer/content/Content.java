// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.List;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import net.sf.saxon.s9api.XdmNode;

/**
 * The store of legal texts. It keeps the legal-text XML of each version as a root of the shared
 * tree.
 */
public interface Content {

  /**
   * Stores a legal text. It writes only the nodes that are not stored yet. If the same text is
   * stored again, nothing is written.
   *
   * @param legalText the output of the converter, a document whose element is {@code legalText}
   * @return the root of the text
   */
  ContentHash store(XdmNode legalText);

  /**
   * Returns the segments of the text at {@code root}, in order. Returns none if the text is short
   * enough to load whole, or if it is not stored.
   */
  List<Segment> segments(ContentHash root);

  /**
   * Returns the legal text at {@code root}, as it was stored. The returned element is {@code
   * legalText}. Returns none if no text is stored there.
   */
  Optional<Element> read(ContentHash root);

  /**
   * Returns the outline of the text at {@code root}. It reads only the containers and their titles,
   * not the other blocks. Returns none if no text is stored there.
   */
  Optional<Outline> outline(ContentHash root);

  /**
   * Writes the legal text at {@code root} to {@code out} as it was stored. Only the whitespace
   * between elements is different, because legal-text does not hold it. Legal-text keeps its text
   * only in elements of its own.
   *
   * @return whether a text is stored there. If not, nothing is written.
   */
  boolean rebuild(ContentHash root, XMLStreamWriter out) throws XMLStreamException;
}
