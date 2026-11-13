// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Asks Cellar what changed, and has each held act that changed read again once it is quiet. Every
 * instance ticks, and one of them scans each time ({@link ChangeStore#claim}).
 *
 * <ol>
 *   <li>It sends Cellar one SPARQL query: the works and expressions of acts and consolidations that
 *       changed since the watermark, less {@code overlap}. A change becomes visible up to 18
 *       minutes after its date, so it reads the latest part again.
 *   <li>It maps the changes to the held acts ({@link CellarChange#byHeldAct}).
 *   <li>In one transaction, it notes each held act that changed ({@link PendingChange#seen}), moves
 *       the watermark to the latest change it read, and publishes {@link ActChanged} for each act
 *       that is now quiet. The event publication registry keeps the events until {@link
 *       ActRefresher} has read each act.
 * </ol>
 *
 * <p>Cellar is asked outside any transaction. A scan that fails leaves the watermark, so the next
 * scan reads the same changes again.
 */
@Component
class ChangeScan {
  private static final Logger LOG = LoggerFactory.getLogger(ChangeScan.class);

  private final ChangeSource cellar;
  private final ChangeStore store;
  private final ActStore acts;
  private final ApplicationEventPublisher events;
  private final TransactionOperations transactions;
  private final Clock clock;
  private final CatalogueProperties.Changes properties;

  ChangeScan(
      ChangeSource cellar,
      ChangeStore store,
      ActStore acts,
      ApplicationEventPublisher events,
      TransactionOperations transactions,
      Clock clock,
      CatalogueProperties properties) {
    this.cellar = cellar;
    this.store = store;
    this.acts = acts;
    this.events = events;
    this.transactions = transactions;
    this.clock = clock;
    this.properties = properties.changes();
  }

  @Scheduled(
      fixedRateString = "${viewer.catalogue.changes.every}",
      initialDelayString = "${viewer.catalogue.changes.every}")
  void tick() {
    try (var job = MDC.putCloseable("job", "change-scan")) {
      scan();
    }
  }

  /**
   * Scans Cellar once, if this instance claims the scan.
   *
   * @return the acts it had read again, or none if it did not scan
   */
  List<ActIdentifier> scan() {
    // A little less than the interval, so that the next tick of the instance that scanned claims
    // the next scan.
    var claim = store.claim(properties.every().minus(properties.every().dividedBy(10)));
    if (claim.isEmpty()) {
      LOG.debug("Another instance scans Cellar for changes");
      return List.of();
    }
    var since = claim.get().watermark().minus(properties.overlap());
    var start = System.nanoTime();
    List<CellarChange> changes;
    try {
      changes = cellar.since(since);
    } catch (RuntimeException e) {
      LOG.warn(
          "Couldn't scan Cellar for changes since {}, again in {}: {}",
          since,
          properties.every(),
          e.toString());
      return List.of();
    }
    var celexes = new HashSet<String>();
    changes.forEach(change -> celexes.addAll(change.celexes()));
    var touched = CellarChange.byHeldAct(changes, acts.held(celexes));
    var watermark = latest(changes, claim.get().watermark());
    var now = clock.instant();
    var read =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  var pending = store.pending(touched.keySet());
                  store.save(
                      touched.entrySet().stream()
                          .map(
                              each ->
                                  pending
                                      .getOrDefault(
                                          each.getKey(), PendingChange.none(each.getKey()))
                                      .seen(each.getValue(), now, properties.debounce()))
                          .toList());
                  if (!store.advance(claim.get(), watermark)) {
                    LOG.warn("Another instance took over the scan of Cellar: this scan is undone");
                    status.setRollbackOnly();
                    return List.<ActIdentifier>of();
                  }
                  var due = store.due(now);
                  store.save(due.stream().map(each -> each.read(now)).toList());
                  due.forEach(each -> events.publishEvent(new ActChanged(each.act())));
                  return due.stream().map(PendingChange::act).toList();
                }));
    LOG.info(
        "Scanned Cellar since {}: {} changes, {} held acts, {} read again {}, in {} ms",
        since,
        changes.size(),
        touched.size(),
        read.size(),
        read,
        Duration.ofNanos(System.nanoTime() - start).toMillis());
    return read;
  }

  /**
   * Returns the new watermark: the latest date of the changes, or {@code watermark} if none is
   * later. The dates are Cellar's, so the clocks of the instances do not matter.
   */
  static Instant latest(List<CellarChange> changes, Instant watermark) {
    return changes.stream()
        .map(CellarChange::modified)
        .max(Comparator.naturalOrder())
        .filter(each -> each.isAfter(watermark))
        .orElse(watermark);
  }
}
