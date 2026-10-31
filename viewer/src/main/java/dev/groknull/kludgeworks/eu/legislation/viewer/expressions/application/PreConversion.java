// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActVersionsChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Converts an act's versions ahead, when they change (Design: Converting off the request path), so
 * a page seldom waits on a conversion: every version of the act, in each language of {@code
 * viewer.expressions.pre-conversion.languages}. Expressions keep what they convert and skip what
 * they keep; a version that fails is logged, and the rest still converted. Off the publisher's
 * thread.
 */
@Component
@EnableConfigurationProperties(PreConversion.Properties.class)
class PreConversion {
  private static final Logger LOG = LoggerFactory.getLogger(PreConversion.class);

  private final Acts acts;
  private final Expressions expressions;
  private final Properties properties;

  PreConversion(Acts acts, Expressions expressions, Properties properties) {
    this.acts = acts;
    this.expressions = expressions;
    this.properties = properties;
  }

  @Async
  @EventListener
  void on(ActVersionsChanged changed) {
    var versions = acts.find(changed.act()).map(act -> act.timeline().versions()).orElse(List.of());
    for (var version : versions) {
      for (var language : properties.languages()) {
        convert(ExpressionIdentifier.of(version, language));
      }
    }
  }

  private void convert(ExpressionIdentifier id) {
    try {
      expressions.find(id);
    } catch (RuntimeException e) {
      LOG.warn("Couldn't convert {} ahead", id, e);
    }
  }

  /**
   * What is converted ahead ({@code viewer.expressions.pre-conversion.*}, set in application.yaml).
   *
   * @param languages the languages an act's versions are converted in, ahead of being asked for
   */
  @ConfigurationProperties("viewer.expressions.pre-conversion")
  @Validated
  record Properties(@NotEmpty List<Language> languages) {}
}
