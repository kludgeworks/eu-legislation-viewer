// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Looks up acts in the background, on Spring Boot's task executor. The catalogue reads an act from
 * Cellar and keeps it ({@link CatalogueService}). This service only runs that read outside the
 * request, and joins the requests that wait for one act.
 *
 * <p>Each instance keeps its own lookups. A page whose stream reaches another instance starts the
 * lookup there too. The catalogue keeps the act once.
 */
@Service
class LookupService implements Lookups {
  private static final Logger LOG = LoggerFactory.getLogger(LookupService.class);

  /** How long the answer that no act has an identifier is kept. */
  private static final Duration MISSING = Duration.ofMinutes(10);

  private final Acts acts;
  private final Executor executor;

  /** The lookups that run, and those that found no act, by the identifier. */
  private final Cache<Object, CompletableFuture<Optional<Act>>> lookups =
      Caffeine.newBuilder().expireAfterWrite(MISSING).build();

  LookupService(Acts acts, @Qualifier("applicationTaskExecutor") Executor executor) {
    this.acts = acts;
    this.executor = executor;
  }

  @Override
  public CompletableFuture<Optional<Act>> lookUp(Eli eli) {
    return lookUp(eli, "ELI " + eli.path(), () -> acts.catalogued(eli), () -> acts.find(eli));
  }

  @Override
  public CompletableFuture<Optional<Act>> lookUp(ActIdentifier id) {
    return lookUp(id, id.celex(), () -> acts.catalogued(id), () -> acts.find(id));
  }

  private CompletableFuture<Optional<Act>> lookUp(
      Object key, String name, Supplier<Optional<Act>> catalogued, Supplier<Optional<Act>> find) {
    var held = catalogued.get();
    if (held.isPresent()) {
      return CompletableFuture.completedFuture(held);
    }
    return lookups.get(key, each -> start(each, name, find));
  }

  private CompletableFuture<Optional<Act>> start(
      Object key, String name, Supplier<Optional<Act>> find) {
    LOG.info("Looking up {} at the Publications Office", name);
    var started = System.nanoTime();
    var lookup = CompletableFuture.supplyAsync(find, executor);
    // On the executor: a lookup that ends at once must not change the cache while it computes.
    lookup.whenCompleteAsync(
        (act, failure) -> {
          var millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
          if (failure != null) {
            LOG.warn(
                "Looked up {}: the Publications Office did not answer, in {} ms",
                name,
                millis,
                failure);
            lookups.invalidate(key);
          } else if (act.isPresent()) {
            LOG.info("Looked up {}: {}, in {} ms", name, act.get().id().celex(), millis);
            lookups.invalidate(key);
          } else {
            LOG.info("Looked up {}: no act has it, in {} ms", name, millis);
          }
        },
        executor);
    return lookup;
  }
}
