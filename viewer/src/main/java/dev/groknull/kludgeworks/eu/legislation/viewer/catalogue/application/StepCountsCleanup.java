// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.StepCounts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextRemoved;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextReplaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Removes the step counts of a text that the content module replaced, or removed because its
 * version was withdrawn. Its identity is never used again, so its counts would never be read.
 */
@Component
class StepCountsCleanup {
  private static final Logger LOG = LoggerFactory.getLogger(StepCountsCleanup.class);

  private final StepCounts counts;

  StepCountsCleanup(StepCounts counts) {
    this.counts = counts;
  }

  @ApplicationModuleListener
  void on(TextReplaced replaced) {
    var removed = counts.remove(replaced.replaced());
    LOG.info(
        "Removed {} step counts of text {}, replaced by {}",
        removed,
        replaced.replaced(),
        replaced.text());
  }

  @ApplicationModuleListener
  void on(TextRemoved removed) {
    var count = counts.remove(removed.text());
    LOG.info(
        "Removed {} step counts of text {} of {}, whose version was withdrawn",
        count,
        removed.text(),
        removed.celex());
  }
}
