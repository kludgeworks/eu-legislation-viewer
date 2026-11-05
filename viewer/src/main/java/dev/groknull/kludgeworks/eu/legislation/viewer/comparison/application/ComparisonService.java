// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Differ;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Comparisons of the texts the content module stores, each text read whole (Content.read). */
@Service
class ComparisonService implements Comparisons {
  private static final Logger LOG = LoggerFactory.getLogger(ComparisonService.class);

  private final Content content;

  ComparisonService(Content content) {
    this.content = content;
  }

  @Override
  public Optional<Comparison> between(ContentHash from, ContentHash to) {
    var start = System.nanoTime();
    var earlier = content.read(from);
    var later = content.read(to);
    if (earlier.isEmpty() || later.isEmpty()) {
      LOG.info("Can't compare {} with {}: not stored", from, to);
      return Optional.empty();
    }
    var comparison = new Comparison(from, to, Differ.differences(earlier.get(), later.get()));
    LOG.info(
        "Compared {} with {}: {} differences in {} ms",
        from,
        to,
        comparison.differences().size(),
        (System.nanoTime() - start) / 1_000_000);
    return Optional.of(comparison);
  }
}
