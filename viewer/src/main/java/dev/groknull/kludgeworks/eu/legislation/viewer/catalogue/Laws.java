// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Gives the text and the law of an act over time. A page and the API ask it, so they show the same
 * answer.
 */
public interface Laws {

  /**
   * Returns the text and the law of {@code act} over time, in {@code language}, worked out one time
   * for the same act and texts.
   *
   * @param texts the tags of the texts in {@code language} that are built, by version
   */
  LawOverTime of(Act act, Language language, Map<Version, TextTags> texts);

  /**
   * Returns the version of {@code act} that a reader in {@code language} gets on {@code day}, or
   * empty before the first version of the act. The version can lack the language.
   */
  Optional<Version> versionOn(Act act, LocalDate day, Language language);
}
