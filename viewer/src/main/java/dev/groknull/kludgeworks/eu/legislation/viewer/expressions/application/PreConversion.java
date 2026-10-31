// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Converts ahead the versions a reader is likely to open next (Design: Converting off the request
 * path): once a version of an act is read, the one before it and the one after, in the reader's
 * language, so comparing with the previous version or stepping through the timeline seldom waits on
 * a conversion. What readers open drives it, not what changes in Cellar: the viewer converts what
 * is read. Expressions keep what they convert and skip what they keep; a version that fails is
 * logged; one not in the reader's language is skipped. Off the reader's thread.
 */
@Component
class PreConversion {
  private static final Logger LOG = LoggerFactory.getLogger(PreConversion.class);

  private final Expressions expressions;
  private final Executor executor;

  /**
   * @param executor where the conversions run: Spring Boot's task executor, on virtual threads
   */
  PreConversion(Expressions expressions, @Qualifier("applicationTaskExecutor") Executor executor) {
    this.expressions = expressions;
    this.executor = executor;
  }

  /** Converts, in the background, the versions of {@code act} either side of {@code read}. */
  void around(Act act, Version read, Language language) {
    var timeline = act.timeline();
    Stream.of(timeline.predecessor(read), timeline.successor(read))
        .flatMap(Optional::stream)
        .filter(version -> version.languages().contains(language))
        .forEach(version -> executor.execute(() -> convert(version, language)));
  }

  private void convert(Version version, Language language) {
    var id = ExpressionIdentifier.of(version, language);
    try {
      expressions.find(id, Objects.requireNonNull(version.formexModified()));
    } catch (RuntimeException e) {
      LOG.warn("Couldn't convert {} ahead", id, e);
    }
  }
}
