// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;

/**
 * The texts of versions. They are built in the background. When a page of an act is first opened in
 * a language, the texts of all its versions in that language are built at once. Each change to the
 * state of a text is a {@link PreparationChanged} event.
 */
public interface Texts {

  /**
   * Returns the act prepared in the language, with the text of each of its versions in that
   * language. A text that is not stored yet is asked for, to be converted. There are no texts in a
   * language that none of its versions is in.
   */
  Preparation prepare(Act act, Language language);

  /**
   * Tries again the act's texts in the language that failed in a way that trying again may help.
   * {@code Failure.retryable} tells which these are. Each text is asked for again, from its first
   * attempt. If the act's preparation had settled, it starts again.
   */
  void retry(Act act, Language language);

  /** Returns the state of the act's preparation in the language, as stored. It asks for nothing. */
  Preparation preparation(Act act, Language language);
}
