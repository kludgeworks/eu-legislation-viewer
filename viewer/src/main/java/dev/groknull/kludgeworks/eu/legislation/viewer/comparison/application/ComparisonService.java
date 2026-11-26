// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ConsecutiveTextsBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.WhoMadeAChange;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Differ;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.DifferVersion;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Compares the texts that the content module stores.
 *
 * <ul>
 *   <li>A kept comparison comes from the store, if the rules of this build worked it out ({@link
 *       DifferVersion}).
 *   <li>Otherwise the service reads both texts whole with {@link Content#read}, works out the
 *       comparison on the executor, and keeps it. It keeps it in place of one that other rules
 *       worked out. It does this for the request that asks for the comparison, or before the
 *       request, on {@link ConsecutiveTextsBuilt}: a reader will probably compare the texts of two
 *       consecutive versions.
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
  private record Pair(TextId from, TextId to) {}

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
  public Optional<Comparison> between(TextId from, TextId to) {
    try {
      return comparing(from, to).join();
    } catch (CompletionException e) {
      throw e.getCause() instanceof RuntimeException cause ? cause : e;
    }
  }

  @Override
  public Map<String, Version> appeared(
      Timeline timeline, Map<Version, Text> texts, Version from, Version to, Set<String> eIds) {
    return WhoMadeAChange.firstTextWithTheChange(
        timeline,
        from,
        to,
        eIds,
        version ->
            timeline
                .predecessor(version)
                .flatMap(before ->
                    texts.get(before) instanceof Text.Ready earlier
                            && texts.get(version) instanceof Text.Ready later
                        ? between(earlier.text(), later.text())
                        : Optional.empty())
                .map(comparison ->
                    comparison.differences().stream()
                        .map(Difference::eId)
                        .collect(Collectors.toSet())));
  }

  @Override
  public Optional<Difference> difference(TextId from, TextId to, String eId) {
    return between(from, to)
        .flatMap(comparison ->
            comparison.differences().stream().filter(each -> each.eId().equals(eId)).findFirst())
        .flatMap(difference ->
            comparisons
                .whole(from, to, eId)
                .map(
                    whole -> DifferenceBuilder.builder(difference).shown(whole).whole(null).build())
                // Not cut down, or an annex whose whole occurrence is lost
                .or(() -> difference.whole() == null ? Optional.of(difference) : Optional.empty()));
  }

  /**
   * Returns the future comparison of {@code from} with {@code to}. It is the kept one, the one that
   * is being worked out already, or one that is worked out now. The future fails in the same way
   * that {@link #between} throws.
   */
  CompletableFuture<Optional<Comparison>> comparing(TextId from, TextId to) {
    var kept = comparisons.find(from, to, DifferVersion.value());
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
                  .find(from, to, DifferVersion.value())
                  .or(() ->
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
  private Optional<Comparison> compare(TextId from, TextId to) {
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
            .observe(() -> {
              var compared = Differ.differences(earlier.get(), later.get());
              return new Comparison(
                  from, to, compared.differences(), compared.closing(), compared.withoutPreamble());
            });
    var diffed = System.nanoTime();
    var stored = comparisons.add(comparison, DifferVersion.value());
    var kept = System.nanoTime();
    LOG.info(
        "Compared text {} with {} (differ {}): {} differences in {} ms (read {} ms, diff {} ms,"
            + " keep {} ms)",
        from,
        to,
        DifferVersion.value(),
        comparison.differences().size(),
        (kept - start) / 1_000_000,
        (read - start) / 1_000_000,
        (diffed - read) / 1_000_000,
        (kept - diffed) / 1_000_000);
    return Optional.of(stored);
  }

  /**
   * Works out the comparison of two consecutive texts before a reader asks for it. It runs outside
   * any transaction: it waits for the work on the executor, which needs a connection of its own. A
   * transaction, even an empty one, keeps a connection for the listener while it waits. When the
   * texts of an act are all converted already, every pair of consecutive texts arrives at once, and
   * the listeners would hold every connection of the pool while their work waits for one.
   */
  @Async
  @TransactionalEventListener
  void on(ConsecutiveTextsBuilt built) {
    between(built.earlier(), built.later());
  }
}
