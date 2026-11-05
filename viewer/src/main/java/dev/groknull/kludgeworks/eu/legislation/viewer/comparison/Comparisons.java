// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.Optional;

/** Comparisons of texts the content module stores. */
public interface Comparisons {

  /**
   * How the text at {@code to} differs from the text at {@code from}; none if either isn't stored.
   */
  Optional<Comparison> between(ContentHash from, ContentHash to);
}
