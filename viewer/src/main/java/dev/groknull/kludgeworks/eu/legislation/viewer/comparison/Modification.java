// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
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

  /**
   * Returns the modification of one step of {@code kind} of difference, which {@code act} made
   * (R-WHO-13). {@code tagged} is the modification that the act's tag, or Cellar, gives.
   *
   * <ul>
   *   <li>A corrigendum corrected it.
   *   <li>The first step of an inserted occurrence inserted it. The last step of a deleted
   *       occurrence deleted it.
   *   <li>Another step replaced or renumbered it, as the tag says. Else it amended it: words were
   *       inserted in, or deleted from, an occurrence that is still there.
   * </ul>
   */
  public static Modification ofStep(
      Kind kind, boolean first, boolean last, ActIdentifier act, Modification tagged) {
    if (act.corrigendum()) {
      return CORRECTED;
    }
    if (first && kind == Kind.INSERTED) {
      return INSERTED;
    }
    if (last && kind == Kind.DELETED) {
      return DELETED;
    }
    return tagged == REPLACED || tagged == RENUMBERED ? tagged : AMENDED;
  }

  /**
   * Returns the modification of {@code kind} of difference where no act is known to have made it
   * (R-WHO-14): inserted, deleted, or amended for an occurrence in both texts whose wording
   * changed.
   */
  public static Modification of(Kind kind) {
    return switch (kind) {
      case INSERTED -> INSERTED;
      case DELETED -> DELETED;
      case CHANGED -> AMENDED;
    };
  }

  /**
   * Returns the modification that Cellar's role of an amendment gives (R-WHO-23): a replacement
   * replaced, an addition inserted, a deletion or a repeal deleted, an amendment amended.
   *
   * <p>A role that changes when the words apply, not the words (an extension, a suspension, a
   * deferral), or that Cellar does not type, gives no modification of the words. Such an amendment
   * never reaches a sidenote: only the amendments that change the words count (R-WHO-08, {@code
   * Act.amended}).
   *
   * @throws IllegalArgumentException for a role that does not change the words
   */
  public static Modification of(Amendment.Role role) {
    return switch (role) {
      case REPLACEMENT -> REPLACED;
      case DELETION, REPEAL -> DELETED;
      case AMENDMENT -> AMENDED;
      case ADDITION -> INSERTED;
      case EXTENSION, SUSPENSION, DEFERRAL, OTHER ->
          throw new IllegalArgumentException(
              "A " + role + " does not change the words, so it has no modification (R-WHO-08)");
    };
  }
}
