// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An expression by its document and its language: 02015R1998-20260322 in English (Cellar's
 * celex/02015R1998-20260322.ENG).
 *
 * @param document the document's CELEX: a version's (Version.celex), or a corrigendum's
 * @param language its language
 */
@ValueObject
public record ExpressionIdentifier(String document, Language language) {

  /** {@code version} in {@code language}. */
  public static ExpressionIdentifier of(Version version, Language language) {
    return new ExpressionIdentifier(version.celex(), language);
  }

  @Override
  public String toString() {
    return document + " " + language.code();
  }
}
