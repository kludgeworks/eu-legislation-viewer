// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.ComparisonKept;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextRemoved;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextReplaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Deletes the comparisons of texts that the content module no longer has.
 *
 * <ul>
 *   <li>When a text is replaced, or removed because its version was withdrawn, the comparisons from
 *       it and to it are deleted. Its identity is never used again, so they would never be read.
 *   <li>A comparison can be kept after that, if it was worked out while the text was replaced. So
 *       when a comparison is kept, its texts are checked, and it is deleted if one is gone.
 *       Whichever comes last, the deletion or the check, deletes it.
 * </ul>
 */
@Component
class ComparisonCleanup {
  private static final Logger LOG = LoggerFactory.getLogger(ComparisonCleanup.class);

  private final ComparisonStore comparisons;
  private final Content content;

  ComparisonCleanup(ComparisonStore comparisons, Content content) {
    this.comparisons = comparisons;
    this.content = content;
  }

  @ApplicationModuleListener
  void on(TextReplaced replaced) {
    var removed = comparisons.remove(replaced.replaced());
    LOG.info(
        "Removed {} comparisons of text {}, replaced by {}",
        removed,
        replaced.replaced(),
        replaced.text());
  }

  @ApplicationModuleListener
  void on(TextRemoved removed) {
    var count = comparisons.remove(removed.text());
    LOG.info(
        "Removed {} comparisons of text {} of {}, whose version was withdrawn",
        count,
        removed.text(),
        removed.celex());
  }

  @ApplicationModuleListener
  void on(ComparisonKept kept) {
    if (content.contains(kept.from()) && content.contains(kept.to())) {
      return;
    }
    if (comparisons.remove(kept.from(), kept.to())) {
      LOG.info(
          "Removed the comparison of text {} with {}, kept after one of them was replaced",
          kept.from(),
          kept.to());
    }
  }
}
