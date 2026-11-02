// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;

/** Versions' texts: built in the background on their first read. */
public interface Texts {

  /**
   * The version's text in the language: ready if it is built; otherwise it is scheduled to be,
   * unless it failed, or the version has no text in the language.
   */
  Text text(Version version, Language language);
}
