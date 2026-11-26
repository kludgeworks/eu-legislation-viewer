// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import java.util.Locale;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What an act did to an occurrence in one step. A tag of legal-text names the act and its
 * modification: replaced, inserted, deleted or renumbered. Akoma Ntoso calls it a textual
 * modification, and the EU's authority table calls these the types of a modification.
 */
@ValueObject
public enum Modification {
  /** New text added. */
  INSERTED,
  /** The text is gone. */
  DELETED,
  /** New text instead of the old. */
  REPLACED,
  /** The same text under a new number. */
  RENUMBERED,
  /** Words inserted in, or deleted from, an occurrence that is still there. */
  AMENDED,
  /** A corrigendum corrected it. */
  CORRECTED;

  /** Returns the modification as legal-text writes it. For example, {@code replaced}. */
  public String word() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Returns the modification that legal-text writes as {@code word}: {@code replaced}, {@code
   * inserted}, {@code deleted} or {@code renumbered}.
   *
   * @throws IllegalArgumentException for any other word
   */
  public static Modification of(String word) {
    return valueOf(word.toUpperCase(Locale.ROOT));
  }
}
