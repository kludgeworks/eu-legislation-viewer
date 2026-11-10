// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import net.sf.saxon.s9api.XdmNode;

/**
 * The store of legal texts. It keeps the legal-text XML of each version, known by its root: the
 * hash of its tree.
 */
public interface Content {

  /**
   * Stores a legal text. It writes only the chunks that are not stored yet. If the same text is
   * stored again, nothing is written.
   *
   * @param legalText the output of the converter, a document whose element is {@code legalText}
   * @return the root of the text
   */
  ContentHash store(XdmNode legalText);

  /**
   * Returns the legal text at {@code root}, as it was stored. The returned element is {@code
   * legalText}. Returns none if no text is stored there.
   */
  Optional<Element> read(ContentHash root);

  /**
   * Returns the outline of the text at {@code root}, kept when the text was stored. Returns none if
   * no text is stored there.
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
