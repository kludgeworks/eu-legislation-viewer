// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.time.Instant;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;

/**
 * All expressions: every version of every act, in each language, its text converted from the Formex
 * Cellar holds; through {@code infra}. Which version applies on a date is the application's
 * question (PointInTime), not this one's.
 */
@Repository
public interface Expressions {

  /**
   * The expression with this identity, its document in its language, from the Formex Cellar last
   * modified on {@code formexModified} (Version.formexModified): a text made from older Formex
   * isn't given.
   *
   * @return none if Cellar has no Formex of the document in the language
   */
  Optional<Expression> find(ExpressionIdentifier id, Instant formexModified);

  /**
   * The version of the converter the texts are made with: a text changes with it, as with its
   * Formex.
   */
  String converterVersion();
}
