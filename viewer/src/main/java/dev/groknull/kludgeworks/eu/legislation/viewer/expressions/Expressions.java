// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.util.Optional;
import java.util.Set;
import org.jmolecules.ddd.annotation.Repository;

/**
 * All expressions: every version of every act, in each language, its text converted from the Formex
 * Cellar holds; through {@code infra}. Which version applies on a date is the application's
 * question (PointInTime), not this one's.
 */
@Repository
public interface Expressions {

  /**
   * The expression with this identity: its document, in its language.
   *
   * @return none if Cellar has no Formex of the document in the language
   */
  Optional<Expression> find(ExpressionIdentifier id);

  /**
   * The languages the document {@code document} is expressed in, as Cellar has Formex of it: those
   * {@link #find} can give a text in. Not every version has all 24 (2015/1998's of 22 March 2026
   * has no Irish).
   *
   * @param document the document's CELEX: a version's (Version.celex)
   * @return none for a document Cellar has no Formex of, or doesn't know
   */
  Set<Language> languages(String document);
}
