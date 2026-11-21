// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Looks up, in the background, the acts that the catalogue does not have yet. Asking the
 * Publications Office takes seconds, so a page does not wait for it. It answers at once that it
 * looks the act up, and gets the act when the lookup ends.
 *
 * <p>A lookup answers:
 *
 * <ul>
 *   <li>the act, which the catalogue then has;
 *   <li>empty, if no act has the identifier. The answer is kept for a while, so that the page that
 *       asks again can say so at once;
 *   <li>an exception, if the Publications Office did not answer. The answer is not kept: the next
 *       lookup asks again.
 * </ul>
 *
 * <p>Two lookups of one act at the same time are one lookup.
 */
public interface Lookups {

  /**
   * Returns the lookup of the act that {@code eli} names. It is done if the catalogue has the act.
   * Else it starts, unless it runs already or its answer is kept.
   */
  CompletableFuture<Optional<Act>> lookUp(Eli eli);

  /** Returns the lookup of the act with the CELEX number {@code id}, as {@link #lookUp(Eli)}. */
  CompletableFuture<Optional<Act>> lookUp(ActIdentifier id);
}
