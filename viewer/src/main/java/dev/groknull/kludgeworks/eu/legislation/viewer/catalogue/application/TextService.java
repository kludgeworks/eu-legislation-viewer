// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.ComparisonWanted;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Ingest;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * A version's text: its root once ingest has built it (ExpressionBuilt, kept in the module's own
 * transaction), else scheduled. One built whose event isn't kept yet, or was lost, is ingest's
 * root, as ingest says. A text kept whose version's neighbours in the language are built too asks
 * for their comparisons ahead of a reader (ComparisonWanted): with the version before, and after.
 */
@Service
class TextService implements Texts {
  private static final Logger LOG = LoggerFactory.getLogger(TextService.class);

  private final ExpressionStore expressions;
  private final ActStore acts;
  private final Ingest ingest;
  private final ApplicationEventPublisher events;

  TextService(
      ExpressionStore expressions, ActStore acts, Ingest ingest, ApplicationEventPublisher events) {
    this.expressions = expressions;
    this.acts = acts;
    this.ingest = ingest;
    this.events = events;
  }

  @Override
  public Text text(Version version, Language language) {
    if (!version.languages().contains(language)) {
      return new Text.Missing();
    }
    var root = expressions.root(version.celex(), language);
    if (root.isPresent()) {
      return new Text.Ready(root.get());
    }
    var status = ingest.schedule(version.celex(), language);
    return switch (status.state()) {
      case BUILT -> {
        // built, and its event not yet kept (or lost): ingest's root is the text's
        LOG.debug("{} in {} is built, not yet kept", version.celex(), language.code());
        yield new Text.Ready(Objects.requireNonNull(status.root()));
      }
      case FAILED -> new Text.Failed(Objects.requireNonNull(status.failure()));
      case QUEUED, RUNNING -> new Text.Building(status);
    };
  }

  @ApplicationModuleListener
  void on(ExpressionBuilt built) {
    expressions.keep(built.celex(), built.language(), built.root(), built.converter());
    LOG.info("{} in {} is now {}", built.celex(), built.language().code(), built.root());
    var timeline =
        acts.findByVersion(built.celex()).flatMap(act -> act.timeline().in(built.language()));
    if (timeline.isEmpty()) {
      return;
    }
    var version =
        timeline.get().versions().stream()
            .filter(each -> each.celex().equals(built.celex()))
            .findFirst()
            .orElseThrow();
    timeline
        .get()
        .predecessor(version)
        .flatMap(before -> expressions.root(before.celex(), built.language()))
        .ifPresent(before -> want(before, built.root()));
    timeline
        .get()
        .successor(version)
        .flatMap(after -> expressions.root(after.celex(), built.language()))
        .ifPresent(after -> want(built.root(), after));
  }

  private void want(ContentHash from, ContentHash to) {
    LOG.debug("Comparison of {} with {} wanted", from, to);
    events.publishEvent(new ComparisonWanted(from, to));
  }
}
