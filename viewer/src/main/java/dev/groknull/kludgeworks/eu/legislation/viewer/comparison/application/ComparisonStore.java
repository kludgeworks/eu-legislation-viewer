// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.ComparisonKept;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.util.Optional;

/**
 * The comparisons that were worked out, by the compared texts, each with the version of the rules
 * that worked it out.
 */
public interface ComparisonStore {

  /**
   * Returns how the text {@code from} became the text {@code to}, if it is kept and the rules of
   * {@code version} worked it out. Each difference is as the store keeps it ({@link
   * Difference#kept}).
   */
  Optional<Comparison> find(TextId from, TextId to, String version);

  /**
   * Returns the unit of the difference at {@code eId} in the comparison of {@code from} with {@code
   * to} whole, as the view shows it, if the store keeps it apart. It keeps it apart if the
   * comparison has it cut down ({@link Difference#kept}).
   */
  Optional<Element> whole(TextId from, TextId to, String eId);

  /**
   * Keeps the comparison that the rules of {@code version} worked out, in place of one of another
   * version. It keeps each difference as {@link Difference#kept} gives it, and the whole unit of
   * each that this cuts down apart. The comparison can be kept already if another caller also
   * worked it out. If it was not, it publishes {@link ComparisonKept} in the same transaction.
   *
   * @return the comparison as the store keeps it, as {@link #find} gives it
   */
  Comparison add(Comparison comparison, String version);

  /**
   * Removes every comparison of {@code text}, from it or to it.
   *
   * @return how many were removed
   */
  int remove(TextId text);

  /**
   * Removes the comparison of {@code from} with {@code to}.
   *
   * @return whether it was kept
   */
  boolean remove(TextId from, TextId to);
}
