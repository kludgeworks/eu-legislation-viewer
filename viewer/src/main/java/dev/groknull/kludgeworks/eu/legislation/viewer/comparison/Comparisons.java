// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.Optional;

/**
 * Comparisons of stored texts. Each comparison is worked out once, the first time that it is asked
 * for, and then kept.
 */
public interface Comparisons {

  /**
   * Returns how the text at {@code from} became the text at {@code to}. The comparison is the kept
   * one, or it is worked out now. Returns none if one of the texts is not stored.
   */
  Optional<Comparison> between(ContentHash from, ContentHash to);
}
