// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.Optional;

/** The comparisons worked out, by the roots of the texts compared. */
public interface ComparisonStore {

  /** How the text at {@code from} became the one at {@code to}, if it's kept. */
  Optional<Comparison> find(ContentHash from, ContentHash to);

  /** Keeps the comparison, unless it's kept already (another worked it out too). */
  void add(Comparison comparison);
}
