// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The act that a reader names with its CELEX number or its ELI. The home page asks here what the
 * viewer has for the words of the reader.
 *
 * <p>Each method returns empty for words that name no act: they are title words, and go to the
 * results of a search.
 */
public interface NamedActs {

  /**
   * Returns what a search has for {@code words}. It does not wait for the Publications Office: if
   * the catalogue does not have the act yet, the viewer looks it up in the background, and the
   * answer is {@link NamedAct.LookingUp}.
   *
   * @param kind the kind of act that the search is for. Only a search of all kinds takes an
   *     identifier of the index for the name of an act.
   */
  Optional<NamedAct> searched(String words, Search.Kind kind);

  /**
   * Returns what the lookup of {@code words} found. It waits for the lookup. The answer is {@link
   * NamedAct.Unanswered} if the Publications Office does not answer in time.
   */
  Optional<NamedAct> lookedUp(String words);

  /**
   * Returns the lookup of {@code words} that runs, or starts one. A page waits on it.
   *
   * @return the name, and whether the lookup found an act. The future fails if the Publications
   *     Office did not answer.
   */
  Optional<Running> lookingUp(String words);

  /**
   * A lookup that runs.
   *
   * @param name how the words name the act
   * @param found completes with whether the lookup found an act
   */
  record Running(NamedAct.Name name, CompletableFuture<Boolean> found) {}
}
