// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;

/**
 * Versions' texts: built in the background, an act's every version in a language at once, when any
 * of its pages is first opened in it. Each change to how one stands is PreparationChanged.
 */
public interface Texts {

  /**
   * The act prepared in the language: each of its versions' text in it, scheduled to be built if it
   * isn't, unless it failed; no texts in a language none of its versions is in.
   */
  Preparation prepare(Act act, Language language);

  /**
   * Tries again those of the act's texts in the language that failed in a way trying again may help
   * (Failure.retryable): each queued again, from its first attempt, and the act's preparation
   * started again if it had settled.
   */
  void retry(Act act, Language language);

  /** How the act's preparation in the language stands, as kept: nothing scheduled. */
  Preparation preparation(Act act, Language language);
}
