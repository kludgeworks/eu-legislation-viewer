// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.time.LocalDate;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;

/**
 * All legal texts: the text of every version of every act, in each language, converted from the
 * Formex Cellar holds; through {@code infra}.
 */
@Repository
public interface LegalTexts {

  /**
   * The text of the version of {@code act} that applies on {@code date} (acts chooses it), in
   * {@code language}.
   *
   * @return none for an act Cellar doesn't know, a date before its text first applies, or a version
   *     Cellar has no Formex of in {@code language}
   */
  Optional<LegalText> find(ActIdentifier act, LocalDate date, Language language);
}
