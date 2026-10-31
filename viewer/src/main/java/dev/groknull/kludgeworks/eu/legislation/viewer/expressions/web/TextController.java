// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.ActResources;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.PointInTime;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * A version's text in a language (URLs and API: public API), by its language tag, as HAL
 * (TextAssembler). A text not converted yet is waited for a while (viewer.expressions.wait-for),
 * then 503 with Retry-After while the conversion goes on (Design: Slow responses); Cellar failing
 * is 502 (CellarProblems), a text the converter can't give a problem of its own
 * (ConversionProblems). A language that isn't an EU one, or one the version isn't in, is 404. As
 * application/hal+json or plain application/json; cacheable as long as the act is kept
 * (ActResources), and tagged (TextAssembler.etag), so a client that has it is answered 304 without
 * the text being read.
 */
@RestController
@RequestMapping(
    path = "/api/acts/{act}/versions/{date:[0-9]{4}-[0-9]{2}-[0-9]{2}}/texts",
    produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class TextController {
  /** A text still being converted. */
  static final URI CONVERSION_IN_PROGRESS =
      URI.create("https://kludgeworks.groknull.dev/problems/conversion-in-progress");

  /** When to ask again for a text still being converted: most take well under a second. */
  static final Duration RETRY_AFTER = Duration.ofSeconds(5);

  private final ActResources resources;
  private final PointInTime pointInTime;
  private final Expressions expressions;
  private final TextAssembler assembler;
  private final Duration waitFor;

  /**
   * @param waitFor how long a request waits for a text being converted
   *     (viewer.expressions.wait-for)
   */
  TextController(
      ActResources resources,
      PointInTime pointInTime,
      Expressions expressions,
      TextAssembler assembler,
      @Value("${viewer.expressions.wait-for}") Duration waitFor) {
    this.resources = resources;
    this.pointInTime = pointInTime;
    this.expressions = expressions;
    this.assembler = assembler;
    this.waitFor = waitFor;
  }

  @GetMapping("/{lang:[a-z]{2}}")
  ResponseEntity<?> text(
      @PathVariable("act") ActIdentifier id,
      @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable("lang") String tag,
      WebRequest request)
      throws InterruptedException {
    var act = resources.act(id);
    var version = resources.version(act, date);
    var language =
        version.languages().stream()
            .filter(l -> l.iso().equals(tag))
            .findFirst()
            .orElseThrow(() -> noText(id, date, tag));
    var etag = TextAssembler.etag(act, expressions.converterVersion());
    var notModified = resources.notModified(request, etag);
    if (notModified.isPresent()) {
      return notModified.get();
    }
    Expression expression;
    try {
      expression =
          pointInTime
              .expression(act, version, language)
              .get(waitFor.toMillis(), TimeUnit.MILLISECONDS)
              .orElseThrow(() -> noText(id, date, tag));
    } catch (TimeoutException e) {
      return converting(id, date, language);
    } catch (ExecutionException e) {
      throw e.getCause() instanceof RuntimeException cause
          ? cause
          : new IllegalStateException(e.getCause());
    }
    return resources.ok(etag, assembler.toModel(act, version, language, expression));
  }

  private static ResponseStatusException noText(ActIdentifier id, LocalDate date, String tag) {
    return new ResponseStatusException(
        HttpStatus.NOT_FOUND, "The version of " + id + " of " + date + " has no text in " + tag);
  }

  /** 503 for a text still being converted: ask again soon, the conversion goes on. */
  private static ResponseEntity<ProblemDetail> converting(
      ActIdentifier id, LocalDate date, Language language) {
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The text of "
                + id
                + " of "
                + date
                + " in "
                + language.iso()
                + " is still being converted from Cellar's Formex; ask again shortly");
    problem.setType(CONVERSION_IN_PROGRESS);
    problem.setTitle("Text being converted");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER.toSeconds()))
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .cacheControl(CacheControl.noStore())
        .body(problem);
  }
}
