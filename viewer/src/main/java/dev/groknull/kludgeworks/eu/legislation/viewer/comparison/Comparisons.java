// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.Optional;

/** Comparisons of stored texts: each worked out once, the first time it's asked for, and kept. */
public interface Comparisons {

  /**
   * How the text at {@code from} became the one at {@code to}: as kept, or worked out now; none if
   * either text isn't stored.
   */
  Optional<Comparison> between(ContentHash from, ContentHash to);
}
