// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * The point-in-time view (Scope): an act's text as it applies on a date. Acts chooses the version
 * (Design: Choosing the version to show), expressions give its text. A text not converted yet takes
 * a while, so the answer is a future (Design: Converting off the request path): a caller bounds its
 * wait (a page answers 503 with Retry-After, or 202 when asked, Design: Slow responses), and the
 * conversion goes on regardless, so the next request finds the text kept. Once a text is given, the
 * versions either side of it are converted ahead (PreConversion).
 */
@Service
public class PointInTime {
  private final Acts acts;
  private final Expressions expressions;
  private final PreConversion preConversion;
  private final Executor executor;

  /**
   * @param executor where the work runs: Spring Boot's task executor, on virtual threads
   *     (spring.threads.virtual.enabled), as the work mostly waits on Cellar and on Valkey
   */
  PointInTime(
      Acts acts,
      Expressions expressions,
      PreConversion preConversion,
      @Qualifier("applicationTaskExecutor") Executor executor) {
    this.acts = acts;
    this.expressions = expressions;
    this.preConversion = preConversion;
    this.executor = executor;
  }

  /**
   * The version of {@code act} that applies on {@code date}, in {@code language}.
   *
   * @return completes at once with a kept text, else once it is converted (here or by another
   *     instance); with none for an act Cellar doesn't know, a date before its text first applies,
   *     or a version Cellar has no Formex of in {@code language}
   */
  public CompletableFuture<Optional<Expression>> expression(
      ActIdentifier act, LocalDate date, Language language) {
    return CompletableFuture.supplyAsync(
        () -> acts.find(act).flatMap(found -> expression(found, date, language)), executor);
  }

  private Optional<Expression> expression(Act act, LocalDate date, Language language) {
    var version = act.versionOn(date);
    if (version.isEmpty()) {
      return Optional.empty();
    }
    var expression = expressions.find(ExpressionIdentifier.of(version.get(), language));
    expression.ifPresent(given -> preConversion.around(act, version.get(), language));
    return expression;
  }
}
