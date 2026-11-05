// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The versions' texts, by the CELEX of the document holding each, as they stand. Ingest's events
 * arrive in any order, each perhaps more than once, so a text only moves on: an attempt to an
 * attempt after it, and none once it is built or failed. Each change says whether it changed it.
 */
public interface ExpressionStore {

  /** The text's root, if it is built. */
  Optional<ContentHash> root(String celex, Language language);

  /** The roots of those of the texts that are built, by CELEX. */
  Map<String, ContentHash> roots(Collection<String> celexes, Language language);

  /** Those of the texts that are kept, as they stand, by CELEX. */
  Map<String, Text> texts(Collection<String> celexes, Language language);

  /** Keeps the texts not kept yet as queued. */
  void queue(Collection<String> celexes, Language language);

  /**
   * The text is being built, on {@code attempt}: unless it is on that attempt or a later already.
   */
  boolean start(String celex, Language language, int attempt);

  /** The text is queued again after {@code attempt} failed: unless a later attempt has started. */
  boolean defer(String celex, Language language, int attempt);

  /** Points the text at the root it was built as, in place of any it had. */
  boolean keep(String celex, Language language, ContentHash root, String converter);

  /** The failed text is queued again, from its first attempt. */
  boolean requeue(String celex, Language language);

  /** The text couldn't be built: unless it is built. */
  boolean fail(String celex, Language language, Failure failure);
}
