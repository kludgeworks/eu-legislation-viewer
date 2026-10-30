// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;

/**
 * All legal texts: the text of every version of every act, in each language, converted from the
 * Formex Cellar holds; through {@code infra}. Which version applies on a date is the application's
 * question (PointInTime), not this one's.
 */
@Repository
public interface LegalTexts {

  /**
   * The text of {@code version}, in {@code language}.
   *
   * @return none if Cellar has no Formex of the version in {@code language}
   */
  Optional<LegalText> find(Version version, Language language);
}
