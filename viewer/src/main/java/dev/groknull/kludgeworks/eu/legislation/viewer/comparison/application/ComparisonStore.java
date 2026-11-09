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
   * Returns how the text at {@code from} became the text at {@code to}, if it is kept. Each
   * difference is as the store keeps it ({@link Difference#kept}).
   */
  Optional<Comparison> find(ContentHash from, ContentHash to);

  /**
   * Returns the unit of the difference at {@code eId} in the comparison of {@code from} with {@code
   * to} whole, as the view shows it, if the store keeps it apart. It keeps it apart if the
   * comparison has it cut down ({@link Difference#kept}).
   */
  Optional<Element> whole(ContentHash from, ContentHash to, String eId);

  /**
   * Keeps the comparison, unless it is kept already. It keeps each difference as {@link
   * Difference#kept} gives it, and the whole unit of each that this cuts down apart. The comparison
   * can be kept already if another caller also worked it out.
   *
   * @return the comparison as the store keeps it, as {@link #find} gives it
   */
  Comparison add(Comparison comparison);
}
