// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;

/**
 * The store of legal texts. It keeps one text for each version (by its CELEX) and language, known
 * by its {@link TextId}.
 */
public interface Content {

  /**
   * Stores the text of a version in a language, in the transaction of the caller.
   *
   * <ul>
   *   <li>If the same text is stored for the version and language already, nothing is written.
   *   <li>If another text is stored, it is deleted, and the new text replaces it. This publishes
   *       {@link TextReplaced}.
   *   <li>If no text is stored, this publishes {@link TextStored}.
   * </ul>
   *
   * @param celex the CELEX of the version, for example {@code 02015R1998-20260322}
   * @param converter the version of the converter that made the text
   * @param legalText the output of the converter, a document whose element is {@code legalText}
   * @return the identity of the stored text
   */
  TextId store(String celex, Language language, String converter, XdmNode legalText);

  /**
   * Returns the text, as it was stored. The returned element is {@code legalText}. Returns none if
   * no such text is stored.
   */
  Optional<Element> read(TextId text);

  /**
   * Returns the text as JSON, as it was stored. The form is JsonML (github.com/mckamey/jsonml):
   * {@code [name, {attributes}, child…]} for an element, or {@code [name, {attributes}, text]} for
   * an element of text. Returns none if no such text is stored.
   */
  Optional<byte[]> json(TextId text);

  /**
   * Returns the unit {@code eId} of the text, with everything in it, as JSON in the same form as
   * {@link #json(TextId)}. Returns none if no such text is stored, or if the text has no unit
   * {@code eId} outside the struck text of a consolidation.
   */
  Optional<byte[]> json(TextId text, String eId);

  /**
   * Returns an element as JSON, in the same form as {@link #json(TextId)}. For example, a unit of a
   * comparison, marked.
   */
  byte[] json(Element element);

  /**
   * Returns the outline of the text, kept when the text was stored. Returns none if no such text is
   * stored.
   */
  Optional<Outline> outline(TextId text);

  /** Returns whether the text is stored. */
  boolean contains(TextId text);

  /** Returns the texts that are stored for these versions in the language, by CELEX. */
  Map<String, TextId> texts(Collection<String> celexes, Language language);
}
