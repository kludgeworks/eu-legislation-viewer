// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.ComparisonWanted;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Differ;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Comparisons of the texts the content module stores: kept ones from the store, others worked out
 * from both texts read whole (Content.read) and kept, on the executor, for the request that asks
 * for one, or ahead of it (ComparisonWanted). One asked for while it's being worked out waits for
 * that one: a reader often asks while it's worked out ahead of them. Working one out is observed
 * (comparison.compare, its diff within it: comparison.diff), its reads the content module's.
 */
@Service
class ComparisonService implements Comparisons {
  private static final Logger LOG = LoggerFactory.getLogger(ComparisonService.class);

  private final Content content;
  private final ComparisonStore comparisons;
  private final ObservationRegistry observations;
  private final Executor executor;
  private final ConcurrentHashMap<Pair, CompletableFuture<Optional<Comparison>>> running =
      new ConcurrentHashMap<>();

  /** The texts a comparison is of. */
  private record Pair(ContentHash from, ContentHash to) {}

  /**
   * @param executor where comparisons are worked out: Spring Boot's task executor
   */
  ComparisonService(
      Content content,
      ComparisonStore comparisons,
      ObservationRegistry observations,
      @Qualifier("applicationTaskExecutor") Executor executor) {
    this.content = content;
    this.comparisons = comparisons;
    this.observations = observations;
    this.executor = executor;
  }

  @Override
  public Optional<Comparison> between(ContentHash from, ContentHash to) {
    try {
      return comparing(from, to).join();
    } catch (CompletionException e) {
      throw e.getCause() instanceof RuntimeException cause ? cause : e;
    }
  }

  /**
   * The comparison of {@code from} with {@code to}, to come: the kept one, the one being worked out
   * already, or one worked out now. Fails as {@link #between} throws.
   */
  CompletableFuture<Optional<Comparison>> comparing(ContentHash from, ContentHash to) {
    var kept = comparisons.find(from, to);
    if (kept.isPresent()) {
      LOG.debug("Comparison of {} with {} is kept", from, to);
      return CompletableFuture.completedFuture(kept);
    }
    var pair = new Pair(from, to);
    var mine = new CompletableFuture<Optional<Comparison>>();
    var theirs = running.putIfAbsent(pair, mine);
    if (theirs != null) {
      LOG.debug("Waiting for the comparison of {} with {}, being worked out already", from, to);
      return theirs;
    }
    mine.whenComplete((comparison, failure) -> running.remove(pair, mine));
    try {
      // kept by now, if another finished it between the look above and this one's start
      mine.completeAsync(
          () ->
              comparisons
                  .find(from, to)
                  .or(
                      () ->
                          Observation.createNotStarted("comparison.compare", observations)
                              .observe(() -> compare(from, to))),
          executor);
    } catch (RuntimeException e) {
      // the executor refused it
      mine.completeExceptionally(e);
    }
    return mine;
  }

  /** Works out the comparison of {@code from} with {@code to}, and keeps it. */
  private Optional<Comparison> compare(ContentHash from, ContentHash to) {
    var start = System.nanoTime();
    var earlier = content.read(from);
    var later = content.read(to);
    if (earlier.isEmpty() || later.isEmpty()) {
      LOG.info("Can't compare {} with {}: not stored", from, to);
      return Optional.empty();
    }
    var read = System.nanoTime();
    var comparison =
        Observation.createNotStarted("comparison.diff", observations)
            .observe(
                () -> {
                  var compared = Differ.differences(earlier.get(), later.get());
                  return new Comparison(
                      from,
                      to,
                      compared.differences(),
                      compared.closing(),
                      compared.withoutPreamble());
                });
    var diffed = System.nanoTime();
    comparisons.add(comparison);
    var kept = System.nanoTime();
    LOG.info(
        "Compared {} with {}: {} differences in {} ms (read {} ms, diff {} ms, keep {} ms)",
        from,
        to,
        comparison.differences().size(),
        (kept - start) / 1_000_000,
        (read - start) / 1_000_000,
        (diffed - read) / 1_000_000,
        (kept - diffed) / 1_000_000);
    return Optional.of(comparison);
  }

  @ApplicationModuleListener
  void on(ComparisonWanted wanted) {
    between(wanted.from(), wanted.to());
  }
}
