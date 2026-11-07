// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.Optional;

/** The comparisons that were worked out, by the roots of the compared texts. */
public interface ComparisonStore {

  /** Returns how the text at {@code from} became the text at {@code to}, if it is kept. */
  Optional<Comparison> find(ContentHash from, ContentHash to);

  /**
   * Keeps the comparison, unless it is kept already. This can occur if another caller also worked
   * it out.
   */
  void add(Comparison comparison);
}
