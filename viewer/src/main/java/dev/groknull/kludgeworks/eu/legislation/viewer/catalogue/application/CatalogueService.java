// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The acts, from the store; an act not kept yet is read from Cellar and kept, so it is asked of
 * Cellar once. Cellar is asked outside any transaction: its answer takes seconds, which no
 * connection is held for.
 */
@Service
class CatalogueService implements Acts {
  private static final Logger LOG = LoggerFactory.getLogger(CatalogueService.class);

  private final ActStore store;
  private final ActSource cellar;

  CatalogueService(ActStore store, ActSource cellar) {
    this.store = store;
    this.cellar = cellar;
  }

  @Override
  public Optional<Act> find(ActIdentifier id) {
    return find(id.celex(), () -> store.find(id), () -> cellar.find(id));
  }

  @Override
  public Optional<Act> find(Eli eli) {
    return find("ELI " + eli, () -> store.find(eli), () -> cellar.find(eli));
  }

  private Optional<Act> find(
      String name, Supplier<Optional<Act>> kept, Supplier<Optional<Act>> fromCellar) {
    var act = kept.get();
    if (act.isPresent()) {
      LOG.debug("{} is in the catalogue", name);
      return act;
    }
    var read = fromCellar.get();
    read.ifPresent(this::keep);
    return read;
  }

  private void keep(Act act) {
    var start = System.nanoTime();
    if (store.add(act)) {
      LOG.info(
          "Catalogued {}: {} version(s), in {} ms",
          act.id(),
          act.timeline().versions().size(),
          (System.nanoTime() - start) / 1_000_000);
    } else {
      LOG.debug("{} was catalogued meanwhile", act.id());
    }
  }
}
