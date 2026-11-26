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

  /**
   * Returns the law of {@code act} on {@code day}: the versions that apply on it, the day they
   * apply until, the deferred application of the act, and its divergences in each language. Empty
   * if no version applies on the day (for example, on a day before the first version).
   */
  Optional<TheLaw> on(Act act, LocalDate day);
}
