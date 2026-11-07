// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reads an act from Cellar again when an {@link ActRefreshDue} event says it is due. It runs in the
 * background, so the page of the act opens at once, as the act was stored.
 *
 * <ul>
 *   <li>The act is stored anew.
 *   <li>The versions that appeared since are prepared in each language that the act has been
 *       prepared in.
 *   <li>Their pages are told by {@code PreparationChanged} events.
 * </ul>
 *
 * <p>Cellar is asked outside any transaction. A refresh that fails is logged and left. The act
 * stays stored as it was, and is due again after {@code viewer.catalogue.refresh-after}.
 */
@Component
class ActRefresher {
  private static final Logger LOG = LoggerFactory.getLogger(ActRefresher.class);

  private final ActSource cellar;
  private final ActStore store;
  private final PreparationStore preparations;
  private final Texts texts;

  ActRefresher(ActSource cellar, ActStore store, PreparationStore preparations, Texts texts) {
    this.cellar = cellar;
    this.store = store;
    this.preparations = preparations;
    this.texts = texts;
  }

  @Async
  @TransactionalEventListener
  void on(ActRefreshDue due) {
    var start = System.nanoTime();
    try {
      var read = cellar.find(due.act());
      if (read.isEmpty()) {
        LOG.warn("Cellar no longer has {}: kept as it was", due.act());
        return;
      }
      var act = read.get();
      var added = store.update(act);
      LOG.info(
          "Refreshed {} from Cellar: {} version(s), {} new, in {} ms",
          act.id(),
          act.timeline().versions().size(),
          added.size(),
          Duration.ofNanos(System.nanoTime() - start).toMillis());
      if (added.isEmpty()) {
        return;
      }
      for (var language : preparations.languages(act.id())) {
        texts.prepare(act, language);
      }
    } catch (RuntimeException e) {
      LOG.warn("Couldn't refresh {} from Cellar, kept as it was: {}", due.act(), e.toString());
    }
  }
}
