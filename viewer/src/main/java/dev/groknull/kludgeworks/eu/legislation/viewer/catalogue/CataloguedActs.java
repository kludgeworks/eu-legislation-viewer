// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Gets an act without waiting for the Publications Office. If the catalogue does not have the act,
 * the lookup starts in the background ({@link Lookups}), and the answer comes at once: the viewer
 * looks the act up ({@link NotCatalogued}).
 */
public final class CataloguedActs {

  private CataloguedActs() {}

  /**
   * Returns the act that {@code eli} names.
   *
   * @throws NotCatalogued if the viewer looks the act up
   * @throws NoSuchAct if no act has the ELI
   */
  public static Act act(Lookups lookups, Eli eli) {
    return done(lookups.lookUp(eli), () -> new NotCatalogued(eli))
        .orElseThrow(() -> new NoSuchAct(eli));
  }

  /**
   * Returns the act with the CELEX number {@code id}.
   *
   * @throws NotCatalogued if the viewer looks the act up
   * @throws NoSuchAct if no act has the CELEX number
   */
  public static Act act(Lookups lookups, ActIdentifier id) {
    return done(lookups.lookUp(id), NotCatalogued::new).orElseThrow(() -> new NoSuchAct(id));
  }

  /**
   * Returns the answer of {@code lookup} if it is done. A lookup that failed is looked up again:
   * the next one asks the Publications Office again.
   */
  private static Optional<Act> done(
      CompletableFuture<Optional<Act>> lookup, Supplier<NotCatalogued> lookingUp) {
    if (lookup.isDone() && !lookup.isCompletedExceptionally()) {
      return lookup.join();
    }
    throw lookingUp.get();
  }
}
