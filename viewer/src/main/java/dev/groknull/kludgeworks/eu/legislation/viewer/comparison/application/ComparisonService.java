// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NeighboursBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
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
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Compares the texts that the content module stores.
 *
 * <ul>
 *   <li>A kept comparison comes from the store.
 *   <li>Otherwise the service reads both texts whole with {@link Content#read}, works out the
 *       comparison on the executor, and keeps it. It does this for the request that asks for the
 *       comparison, or before the request, on {@link NeighboursBuilt}: a reader will probably
 *       compare the texts of two neighbouring versions.
 *   <li>A request for a comparison that is being worked out waits for that work. A reader often
 *       asks while the comparison is being worked out before the reader asked.
 * </ul>
 *
 * <p>The work is observed as {@code comparison.compare}, with the diff in it as {@code
 * comparison.diff}. The content module observes the reads.
 */
@Service
class ComparisonService implements Comparisons {
  private static final Logger LOG = LoggerFactory.getLogger(ComparisonService.class);

  private final Content content;
  private final ComparisonStore comparisons;
  private final ObservationRegistry observations;
  private final Executor executor;
  private final ConcurrentMap<Pair, CompletableFuture<Optional<Comparison>>> running =
      new ConcurrentHashMap<>();

  /** The two texts of a comparison. */
  private record Pair(ContentHash from, ContentHash to) {}

  /**
   * @param executor the executor that works out comparisons. It is the task executor of Spring
   *     Boot.
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
   * Returns the future comparison of {@code from} with {@code to}. It is the kept one, the one that
   * is being worked out already, or one that is worked out now. The future fails in the same way
   * that {@link #between} throws.
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
      // Another caller may have kept it between the first find and this start, so find it again.
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
      // The executor refused the work.
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
  void on(NeighboursBuilt built) {
    between(built.earlier(), built.later());
  }
}
