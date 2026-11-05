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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Comparisons of the texts the content module stores: kept ones from the store, others worked out
 * from both texts read whole (Content.read) and kept, in the request that asks for one, or ahead of
 * it (ComparisonWanted, in the module's own transaction). Working one out is observed
 * (comparison.compare, its diff within it: comparison.diff), its reads the content module's.
 */
@Service
class ComparisonService implements Comparisons {
  private static final Logger LOG = LoggerFactory.getLogger(ComparisonService.class);

  private final Content content;
  private final ComparisonStore comparisons;
  private final ObservationRegistry observations;

  ComparisonService(
      Content content, ComparisonStore comparisons, ObservationRegistry observations) {
    this.content = content;
    this.comparisons = comparisons;
    this.observations = observations;
  }

  @Override
  public Optional<Comparison> between(ContentHash from, ContentHash to) {
    var kept = comparisons.find(from, to);
    if (kept.isPresent()) {
      LOG.debug("Comparison of {} with {} is kept", from, to);
      return kept;
    }
    return Observation.createNotStarted("comparison.compare", observations)
        .observe(() -> compare(from, to));
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
                () -> new Comparison(from, to, Differ.differences(earlier.get(), later.get())));
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
