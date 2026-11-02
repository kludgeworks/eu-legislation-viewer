// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Ingest;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * A version's text: its root once ingest has built it (ExpressionBuilt, kept in the module's own
 * transaction), else scheduled. One built whose event isn't kept yet, or was lost, is ingest's
 * root, as ingest says.
 */
@Service
class TextService implements Texts {
  private static final Logger LOG = LoggerFactory.getLogger(TextService.class);

  private final ExpressionStore expressions;
  private final Ingest ingest;

  TextService(ExpressionStore expressions, Ingest ingest) {
    this.expressions = expressions;
    this.ingest = ingest;
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
  }
}
