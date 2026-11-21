// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Gets the act of a request without waiting for the Publications Office. If the catalogue does not
 * have the act, the lookup starts in the background, and the request answers at once that the
 * viewer looks the act up ({@link NotCatalogued}).
 */
@Component
class Catalogued {
  private final Lookups lookups;

  Catalogued(Lookups lookups) {
    this.lookups = lookups;
  }

  /**
   * Returns the act that {@code eli} names, for a page.
   *
   * @throws NotCatalogued if the viewer looks the act up
   * @throws NoSuchAct if no act has the ELI
   */
  Act act(Eli eli) {
    return answer(lookups.lookUp(eli), eli).orElseThrow(() -> new NoSuchAct(eli));
  }

  /**
   * Returns the act that {@code eli} names, for the API.
   *
   * @throws NotCatalogued if the viewer looks the act up
   * @throws org.springframework.web.server.ResponseStatusException 404 if no act has the ELI
   */
  Act api(Eli eli) {
    return answer(lookups.lookUp(eli), eli).orElseThrow(Requests::notFound);
  }

  /** Returns the act with the CELEX number {@code id}, for the API, as {@link #api(Eli)}. */
  Act api(ActIdentifier id) {
    return answer(lookups.lookUp(id), null).orElseThrow(Requests::notFound);
  }

  /**
   * Returns the answer of {@code lookup} if it is done. A lookup that failed is looked up again:
   * the next one asks the Publications Office again.
   */
  private static Optional<Act> answer(CompletableFuture<Optional<Act>> lookup, @Nullable Eli eli) {
    if (lookup.isDone() && !lookup.isCompletedExceptionally()) {
      return lookup.join();
    }
    throw eli == null ? new NotCatalogued() : new NotCatalogued(eli);
  }
}
