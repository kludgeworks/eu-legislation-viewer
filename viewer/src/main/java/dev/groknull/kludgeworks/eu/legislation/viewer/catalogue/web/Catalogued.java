// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.CataloguedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NoSuchAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NotCatalogued;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import org.springframework.stereotype.Component;

/**
 * Gets the act of a request for a controller that still loads the act itself ({@link
 * CataloguedActs}). For the API, it answers 404 if no act has the identifier.
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
    return CataloguedActs.act(lookups, eli);
  }

  /**
   * Returns the act that {@code eli} names, for the API.
   *
   * @throws NotCatalogued if the viewer looks the act up
   * @throws org.springframework.web.server.ResponseStatusException 404 if no act has the ELI
   */
  Act api(Eli eli) {
    try {
      return CataloguedActs.act(lookups, eli);
    } catch (NoSuchAct missing) {
      throw Requests.notFound();
    }
  }

  /** Returns the act with the CELEX number {@code id}, for the API, as {@link #api(Eli)}. */
  Act api(ActIdentifier id) {
    try {
      return CataloguedActs.act(lookups, id);
    } catch (NoSuchAct missing) {
      throw Requests.notFound();
    }
  }
}
