// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The texts of the versions as they stand, by the CELEX of the document that holds each.
 *
 * <p>The events of ingest arrive in any order, and each event may arrive more than once. Thus a
 * text only moves forward. It moves from an attempt to a later attempt, and it does not move once
 * it is built or failed. Each change returns whether it changed the text.
 */
public interface ExpressionStore {

  /** Returns the text's root, if it is built. */
  Optional<ContentHash> root(String celex, Language language);

  /** Returns the roots of the texts that are built, by CELEX. */
  Map<String, ContentHash> roots(Collection<String> celexes, Language language);

  /** Returns the texts that are stored, as they stand, by CELEX. */
  Map<String, Text> texts(Collection<String> celexes, Language language);

  /** Stores as queued the texts that are not stored yet. */
  void queue(Collection<String> celexes, Language language);

  /**
   * Marks the text as being built, on {@code attempt}. It does nothing if the text is on that
   * attempt or a later one already.
   */
  boolean start(String celex, Language language, int attempt);

  /**
   * Queues the text again after {@code attempt} failed. It does nothing if a later attempt has
   * started.
   */
  boolean defer(String celex, Language language, int attempt);

  /** Points the text at the root it was built as, in place of any it had. */
  boolean keep(String celex, Language language, ContentHash root, String converter);

  /** Queues the failed text again, from its first attempt. */
  boolean requeue(String celex, Language language);

  /**
   * Marks the text as failed, because it could not be built. It does nothing if the text is built.
   */
  boolean fail(String celex, Language language, Failure failure, @Nullable String trace);
}
