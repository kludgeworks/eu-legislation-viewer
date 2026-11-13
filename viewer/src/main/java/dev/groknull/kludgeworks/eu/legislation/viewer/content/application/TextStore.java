// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The store of texts. It keeps one text for each version and language: its JSON, the digest of the
 * JSON, its outline and the converter that made it. All of it is written once, with the text.
 */
public interface TextStore {

  /**
   * Returns the text stored for the version in the language, if there is one. The version and
   * language stay locked until the transaction ends, also when no text is stored yet, so that two
   * writers do not store or replace it at the same time.
   */
  Optional<Kept> lock(String celex, Language language);

  /**
   * Keeps a text. There must be no text for the version in the language.
   *
   * @param json the text as {@code Encoder} writes it
   */
  Added add(
      String celex,
      Language language,
      String converter,
      byte[] digest,
      Outline outline,
      byte[] json);

  /** Deletes the text. */
  void delete(TextId text);

  /**
   * Deletes the texts of the version, in every language.
   *
   * @return the texts deleted, by language
   */
  Map<Language, TextId> delete(String celex);

  /** Returns the JSON of the text, if it is kept. */
  Optional<byte[]> json(TextId text);

  /** Returns the outline of the text, if it is kept. */
  Optional<Outline> outline(TextId text);

  /** Returns whether the text is kept. */
  boolean contains(TextId text);

  /** Returns the texts kept for these versions in the language, by CELEX. */
  Map<String, TextId> texts(Collection<String> celexes, Language language);

  /**
   * A text as it is kept.
   *
   * @param digest the SHA-256 of its JSON
   */
  record Kept(TextId id, byte[] digest) {}

  /**
   * A text that was added.
   *
   * @param bytes how many bytes it takes in the store
   */
  record Added(TextId id, int bytes) {}
}
