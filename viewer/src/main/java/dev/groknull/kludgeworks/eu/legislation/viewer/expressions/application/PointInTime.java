// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * The point-in-time view (Scope): an act's text as it applies on a date. The act chooses the
 * version (Act.versionOn, Design: Choosing the version to show), a step of its own that each format
 * and link shares; this gives that version's text. A text not converted yet takes a while, so the
 * answer is a future (Design: Converting off the request path): a caller bounds its wait (a page
 * answers 503 with Retry-After, or 202 when asked, Design: Slow responses), and the conversion goes
 * on regardless, so the next request finds the text kept. Once a text is given, the versions either
 * side of it are converted ahead (PreConversion).
 */
@Service
public class PointInTime {
  private final Expressions expressions;
  private final PreConversion preConversion;
  private final Executor executor;

  /**
   * @param executor where the work runs: Spring Boot's task executor, on virtual threads
   *     (spring.threads.virtual.enabled), as the work mostly waits on Cellar and on Valkey
   */
  PointInTime(
      Expressions expressions,
      PreConversion preConversion,
      @Qualifier("applicationTaskExecutor") Executor executor) {
    this.expressions = expressions;
    this.preConversion = preConversion;
    this.executor = executor;
  }

  /**
   * The text of {@code version} of {@code act}, in {@code language}.
   *
   * @return completes at once with a kept text, else once it is converted (here or by another
   *     instance); with none for a version Cellar has no Formex of in {@code language}
   * @throws IllegalArgumentException if {@code version} isn't one of {@code act}'s
   */
  public CompletableFuture<Optional<Expression>> expression(
      Act act, Version version, Language language) {
    if (!act.timeline().versions().contains(version)) {
      throw new IllegalArgumentException("Not a version of " + act.id() + ": " + version);
    }
    var formexModified = version.formexModified();
    if (formexModified == null || !version.languages().contains(language)) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    return CompletableFuture.supplyAsync(
        () -> {
          var expression =
              expressions.find(ExpressionIdentifier.of(version, language), formexModified);
          expression.ifPresent(given -> preConversion.around(act, version, language));
          return expression;
        },
        executor);
  }
}
