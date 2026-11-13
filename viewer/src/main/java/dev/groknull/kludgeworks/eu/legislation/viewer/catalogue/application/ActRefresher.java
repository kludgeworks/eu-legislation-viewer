// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Reads a held act from Cellar again when Cellar changed it ({@link ActChanged}). It compares the
 * act as stored with the act as Cellar has it now ({@link Refresh}). In one transaction, it stores
 * the act anew, deletes the versions withdrawn, and publishes one event for each difference. The
 * catalogue prepares the texts that the events name; the content module deletes the texts of a
 * withdrawn version.
 *
 * <p>Cellar is asked outside any transaction. A read again of an act that nothing changed since
 * finds no difference and publishes nothing. If the read fails, it is logged and thrown, so the
 * event publication registry keeps the event, and hands it out again.
 */
@Component
class ActRefresher {
  private static final Logger LOG = LoggerFactory.getLogger(ActRefresher.class);

  private final ActSource cellar;
  private final ActStore store;
  private final ChangeStore changes;
  private final ApplicationEventPublisher events;
  private final TransactionOperations transactions;
  private final Clock clock;
  private final Duration withdrawnAfter;

  ActRefresher(
      ActSource cellar,
      ActStore store,
      ChangeStore changes,
      ApplicationEventPublisher events,
      TransactionOperations transactions,
      Clock clock,
      CatalogueProperties properties) {
    this.cellar = cellar;
    this.store = store;
    this.changes = changes;
    this.events = events;
    this.transactions = transactions;
    this.clock = clock;
    this.withdrawnAfter = properties.changes().withdrawnAfter();
  }

  @Async
  @TransactionalEventListener
  void on(ActChanged changed) {
    try (var act = MDC.putCloseable("act", changed.act().celex())) {
      refresh(changed);
    }
  }

  /**
   * Reads the act again, stores it and publishes its differences.
   *
   * @return the events published
   */
  List<Object> refresh(ActChanged changed) {
    var start = System.nanoTime();
    var stored = store.find(changed.act());
    if (stored.isEmpty()) {
      LOG.debug("{} isn't held: nothing to read again", changed.act());
      return List.of();
    }
    Optional<Act> read;
    try {
      read = cellar.find(changed.act());
    } catch (RuntimeException e) {
      LOG.warn(
          "Couldn't read {} from Cellar again, to be tried again: {}", changed.act(), e.toString());
      throw e;
    }
    if (read.isEmpty()) {
      // Cellar deletes a work and ingests it again with the same identity. The act's versions are
      // withdrawn by their own reads, never because the act is gone for a moment.
      LOG.warn("Cellar has no {} at the moment: kept as it was", changed.act());
      return List.of();
    }
    var now = clock.instant();
    var refresh =
        Refresh.of(stored.get(), store.missing(changed.act()), read.get(), now, withdrawnAfter);
    transactions.executeWithoutResult(
        status -> {
          store.update(read.get(), refresh.missing());
          refresh.withdrawn().forEach(celex -> store.withdraw(changed.act(), celex));
          var recheckAt = refresh.recheckAt();
          if (recheckAt != null) {
            var pending =
                changes
                    .pending(List.of(changed.act()))
                    .getOrDefault(changed.act(), PendingChange.none(changed.act()));
            changes.save(List.of(pending.recheck(recheckAt, now)));
          }
          refresh.events().forEach(events::publishEvent);
        });
    LOG.info(
        "Read {} from Cellar again: {} version(s), {} missing, events {}, in {} ms",
        changed.act(),
        read.get().timeline().versions().size(),
        refresh.missing().size(),
        refresh.events(),
        Duration.ofNanos(System.nanoTime() - start).toMillis());
    return refresh.events();
  }
}
