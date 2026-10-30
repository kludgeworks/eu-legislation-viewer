// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A legal text by the document it is the text of and its language: 02015R1998-20260322 in English.
 *
 * @param document the document's CELEX: a version's (Version.celex), or a corrigendum's
 * @param language its language
 */
@ValueObject
public record LegalTextIdentifier(String document, Language language) {

  /** The text of {@code version} in {@code language}. */
  public static LegalTextIdentifier of(Version version, Language language) {
    return new LegalTextIdentifier(version.celex(), language);
  }

  @Override
  public String toString() {
    return document + " " + language.code();
  }
}
