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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The acts, from the store. An act that is not stored yet is read from Cellar and stored, so Cellar
 * is asked for it once.
 *
 * <p>An act stored for longer than {@code viewer.catalogue.refresh-after} is found as stored. The
 * one opening of it that claims the refresh publishes an {@link ActRefreshDue} event. The act is
 * then read again in the background.
 *
 * <p>Cellar is asked outside any transaction. Its answer takes seconds, and no connection is held
 * for that time.
 */
@Service
class CatalogueService implements Acts {
  private static final Logger LOG = LoggerFactory.getLogger(CatalogueService.class);

  private final ActStore store;
  private final ActSource cellar;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transactions;
  private final CatalogueProperties properties;

  CatalogueService(
      ActStore store,
      ActSource cellar,
      ApplicationEventPublisher events,
      TransactionTemplate transactions,
      CatalogueProperties properties) {
    this.store = store;
    this.cellar = cellar;
    this.events = events;
    this.transactions = transactions;
    this.properties = properties;
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
      refreshIfDue(act.get().id());
      return act;
    }
    var read = fromCellar.get();
    read.ifPresent(this::keep);
    return read;
  }

  /** Claims the act's refresh if it is due, and asks for it once the claim is committed. */
  private void refreshIfDue(ActIdentifier id) {
    transactions.executeWithoutResult(
        status -> {
          if (store.due(id, properties.refreshAfter())) {
            LOG.debug("{} is due to be read from Cellar again", id);
            events.publishEvent(new ActRefreshDue(id));
          }
        });
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
