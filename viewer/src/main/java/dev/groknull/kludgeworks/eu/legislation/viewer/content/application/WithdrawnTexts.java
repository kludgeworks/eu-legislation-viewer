// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextRemoved;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.VersionWithdrawn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Deletes the texts of a version that was withdrawn, in every language, and publishes {@link
 * TextRemoved} for each, in the same transaction. A version withdrawn again finds no texts, and
 * publishes nothing.
 */
@Component
class WithdrawnTexts {
  private static final Logger LOG = LoggerFactory.getLogger(WithdrawnTexts.class);

  private final TextStore store;
  private final ApplicationEventPublisher events;

  WithdrawnTexts(TextStore store, ApplicationEventPublisher events) {
    this.store = store;
    this.events = events;
  }

  @ApplicationModuleListener
  void on(VersionWithdrawn withdrawn) {
    var removed = store.delete(withdrawn.celex());
    removed.forEach(
        (language, text) ->
            events.publishEvent(new TextRemoved(text, withdrawn.celex(), language)));
    LOG.info(
        "Removed the texts of withdrawn version {}: {} in {} languages",
        withdrawn.celex(),
        removed.values(),
        removed.size());
  }
}
