// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;

/**
 * Versions' texts: built in the background, an act's every version in a language at once, when any
 * of its pages is first opened in it.
 */
public interface Texts {

  /**
   * The act whose versions in a language are {@code timeline} (Timeline.in), prepared: each
   * version's text, scheduled to be built if it isn't, unless it failed.
   */
  Preparation prepare(Timeline timeline, Language language);
}
