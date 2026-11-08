// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.Optional;

/** The comparisons that were worked out, by the roots of the compared texts. */
public interface ComparisonStore {

  /**
   * Returns how the text at {@code from} became the text at {@code to}, if it is kept. An annex
   * inserted whole has only its opening and its size ({@link Difference#opening}).
   */
  Optional<Comparison> find(ContentHash from, ContentHash to);

  /**
   * Returns the annex inserted whole at {@code eId} in the comparison of {@code from} with {@code
   * to}, as the view shows it, if it is kept.
   */
  Optional<Element> annex(ContentHash from, ContentHash to, String eId);

  /**
   * Keeps the comparison, unless it is kept already. It keeps each annex inserted whole apart, and
   * the comparison with only its opening. This can occur if another caller also worked it out.
   */
  void add(Comparison comparison);
}
