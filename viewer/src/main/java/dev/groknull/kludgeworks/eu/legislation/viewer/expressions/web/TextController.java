// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.ActLinks;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.PointInTime;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * A version's text in a language (URLs and API: public API), by its language tag: the legal text as
 * the resource's own state (LegalTextJsonModule), with its links: up to the version; the versions
 * either side, the first and the latest (RFC 5829, RFC 8288) in the same language, a version
 * without it answering 404; what changed since the one before (av:changes, planned: 501); and as
 * alternates the version in each other language it has (hreflang), and the text on EUR-Lex and the
 * expression in Cellar, its Formex among its manifestations. A text not converted yet is waited for
 * a while (viewer.expressions.wait-for), then 503 with Retry-After while the conversion goes on
 * (Design: Slow responses); Cellar failing is 502 (CellarProblems), a text the converter can't give
 * a problem of its own (ConversionProblems). A language that isn't an EU one, or one Cellar has no
 * Formex of the version in, is 404. HAL, as application/hal+json or plain application/json;
 * cacheable as long as the act is kept (viewer.acts.cache-for).
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

  private final Acts acts;
  private final Expressions expressions;
  private final PointInTime pointInTime;
  private final URI cellarResources;
  private final Duration waitFor;
  private final CacheControl cacheControl;

  /**
   * @param cellarResources where Cellar's resource URIs start (viewer.cellar.resources)
   * @param waitFor how long a request waits for a text being converted
   *     (viewer.expressions.wait-for)
   * @param cacheFor how long an act is kept, and so may be cached (viewer.acts.cache-for)
   */
  TextController(
      Acts acts,
      Expressions expressions,
      PointInTime pointInTime,
      @Value("${viewer.cellar.resources}") URI cellarResources,
      @Value("${viewer.expressions.wait-for}") Duration waitFor,
      @Value("${viewer.acts.cache-for}") Duration cacheFor) {
    this.acts = acts;
    this.expressions = expressions;
    this.pointInTime = pointInTime;
    this.cellarResources = cellarResources;
    this.waitFor = waitFor;
    this.cacheControl = CacheControl.maxAge(cacheFor).cachePublic();
  }

  @GetMapping("/{lang:[a-z]{2}}")
  ResponseEntity<?> text(
      @PathVariable("act") ActIdentifier id,
      @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable("lang") String tag)
      throws InterruptedException {
    var act =
        acts.find(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No act " + id));
    var timeline = act.timeline();
    var version =
        timeline.versions().stream()
            .filter(v -> v.date().equals(date))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No version of " + id + " starts on " + date));
    var languages = expressions.languages(version.celex());
    var language =
        languages.stream()
            .filter(l -> l.iso().equals(tag))
            .findFirst()
            .orElseThrow(() -> noText(id, date, tag));
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

    var links = new ArrayList<Link>();
    links.add(ActLinks.text(id, date, language, IanaLinkRelations.SELF));
    links.add(ActLinks.version(id, date, IanaLinkRelations.UP));
    timeline
        .predecessor(version)
        .ifPresent(
            v -> {
              links.add(
                  ActLinks.text(id, v.date(), language, IanaLinkRelations.PREDECESSOR_VERSION));
              links.add(
                  ActLinks.comparisonText(id, v.date(), date, language, VersionController.CHANGES));
            });
    timeline
        .successor(version)
        .ifPresent(
            v ->
                links.add(
                    ActLinks.text(id, v.date(), language, VersionController.SUCCESSOR_VERSION)));
    links.add(ActLinks.text(id, timeline.first().date(), language, IanaLinkRelations.FIRST));
    links.add(
        ActLinks.text(id, timeline.latest().date(), language, IanaLinkRelations.LATEST_VERSION));
    for (var other : languages) {
      if (other != language) {
        links.add(
            ActLinks.text(id, date, other, IanaLinkRelations.ALTERNATE).withHreflang(other.iso()));
      }
    }
    links.add(ActLinks.eurLex("TXT", version.celex(), language, "Read on EUR-Lex"));
    links.add(
        ActLinks.cellar(
            cellarResources,
            version.celex() + "." + language.code(),
            "The expression in Cellar, its Formex among its manifestations"));
    RepresentationModel<?> model = HalModelBuilder.halModelOf(expression).links(links).build();
    return ResponseEntity.ok().cacheControl(cacheControl).body(model);
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
