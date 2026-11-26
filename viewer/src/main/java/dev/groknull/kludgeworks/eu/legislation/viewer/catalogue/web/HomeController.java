// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NamedAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NamedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HomeView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The home page. It has one box, Find an act.
 *
 * <ul>
 *   <li>A CELEX number or ELI opens the act. The local index can also identify an act by an
 *       unambiguous citation.
 *   <li>Other words list matching acts, one page at a time. A filter limits results to a kind of
 *       act, for example {@code ?type=regulation}.
 * </ul>
 *
 * <p>Each search is logged once at INFO.
 *
 * <p>The search is kept in the {@link Frame#SEARCH} cookie for a while. The site bar uses it to
 * link back to the results.
 */
/*
 * A handler takes the frame of its page as a Frame, which the site module gives. The check knows
 * only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
@Controller
class HomeController {
  private static final Logger LOG = LoggerFactory.getLogger(HomeController.class);

  /** The address that looks up an act the catalogue does not have yet. */
  static final String LOOK_UP = "/lookup";

  /** The address of the stream that says when a lookup ends. */
  static final String LOOK_UP_EVENTS = LOOK_UP + "/events";

  /** How long a lookup's stream lasts before the page is asked to connect again. */
  private static final Duration STREAM = Duration.ofMinutes(2);

  /** How many acts a page of results has. */
  static final int PAGE = 20;

  /** How long the way back to a search's results stays in the site bar. */
  private static final Duration REMEMBERED = Duration.ofHours(2);

  private final NamedActs named;
  private final Search search;
  private final Templates templates;

  HomeController(NamedActs named, Search search, Templates templates) {
    this.named = named;
    this.search = search;
    this.templates = templates;
  }

  /**
   * Returns the home page. With {@code q}, it redirects to the act, or it returns the results of
   * the kind {@code type}, at the page {@code page}. Returns 404 for a kind or a page that does not
   * exist.
   */
  @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> home(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "all") String type,
      @RequestParam(defaultValue = "1") int page,
      Frame frame) {
    var kind = Search.Kind.of(type).orElseThrow(Requests::notFound);
    if (page < 1) {
      throw Requests.notFound();
    }
    var words = q.strip();
    if (words.isEmpty()) {
      return page(frame, "Amendment viewer", HomeView.empty());
    }
    var started = System.nanoTime();
    var act = named.searched(words, kind);
    if (act.isPresent()) {
      return show(act.get(), words, frame);
    }
    var query = Search.Query.of(words, kind, (page - 1) * PAGE, PAGE);
    if (query.isEmpty()) {
      LOG.info("Searched for “{}”: no words to search by", typed(words));
      return page(frame, "Amendment viewer", HomeView.none(words, "Type words of an act's title."));
    }
    var found = search.matching(query.get());
    LOG.info(
        "Searched for “{}” by title words {} ({}, page {}): {} acts{}, in {} ms",
        typed(words),
        query.get().words(),
        kind.code(),
        page,
        found.acts().size(),
        found.more() ? " and more" : "",
        millis(started));
    var remembered =
        ResponseCookie.from(
                Frame.SEARCH,
                "q="
                    + URLEncoder.encode(words, StandardCharsets.UTF_8)
                    + (kind == Search.Kind.ALL ? "" : "&type=" + kind.code()))
            .path("/")
            .maxAge(REMEMBERED)
            .httpOnly(true)
            .sameSite("Lax")
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, remembered.toString())
        .contentType(Requests.HTML)
        .body(
            templates
                .catalogueHome(
                    frame,
                    "Acts matching “" + words + "” · Amendment viewer",
                    HomeView.found(words, kind, page, PAGE, found))
                .render());
  }

  /**
   * Looks up the act that {@code q}, a CELEX number or an ELI, names, and goes to it. The home page
   * asks for this address when the lookup of the act found it, and a page without JavaScript goes
   * to it at once: it waits for the lookup, which runs in the background ({@link NamedActs}). If no
   * act has it, the response is the home page that says so. If the Publications Office does not
   * answer, the response is the home page that says so, with 503. Other words go to their results.
   * A lookup is logged when it starts and when it ends.
   */
  @GetMapping(path = LOOK_UP, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> lookUp(@RequestParam String q, Frame frame) {
    var words = q.strip();
    return named
        .lookedUp(words)
        .map(act -> show(act, words, frame))
        .orElseGet(() ->
            ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(Links.home(words, Search.Kind.ALL, 1)))
                .build());
  }

  /**
   * Returns the stream of the lookup of the act that {@code q}, a CELEX number or an ELI, names.
   * The home page waits on it while it looks the act up. The stream joins the lookup that runs, or
   * starts one.
   *
   * <ul>
   *   <li>If the lookup found the act, it sends the event {@code done}, and the page asks the
   *       lookup address, which goes to the act.
   *   <li>Else it sends the fragment of the page that says that no act has it, or that the
   *       Publications Office did not answer.
   * </ul>
   *
   * <p>Returns 404 for words that name no act.
   */
  @GetMapping(path = LOOK_UP_EVENTS, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter lookUpEvents(@RequestParam String q) {
    var words = q.strip();
    var running = named.lookingUp(words).orElseThrow(Requests::notFound);
    var unanswered = unanswered(words, running.name());
    var emitter = new SseEmitter(STREAM.toMillis());
    running
        .found()
        .whenComplete((found, failure) -> {
          try {
            if (failure == null && found) {
              emitter.send(SseEmitter.event().name("done").data(""));
            } else {
              var ended =
                  failure == null
                      ? templates.catalogueLookupEnded(unanswered, noAct(words))
                      : templates.catalogueLookupEnded(unanswered, null);
              emitter.send(SseEmitter.event().data(ended.render()));
            }
            emitter.complete();
          } catch (IOException | IllegalStateException e) {
            // The page went away, so its stream is no longer necessary.
            LOG.debug("A lookup's stream closed: {}", e.toString());
          }
        });
    return emitter;
  }

  /**
   * Returns the answer for words that name one act:
   *
   * <ul>
   *   <li>a redirect to the act, if the catalogue has it
   *   <li>the home page that says that no act has the number
   *   <li>the home page that says it looks the act up, and waits for the lookup
   *   <li>the home page that says that the Publications Office did not answer, with 503
   * </ul>
   */
  private ResponseEntity<String> show(NamedAct act, String words, Frame frame) {
    var name = act.name();
    return switch (act) {
      case NamedAct.Found found ->
          ResponseEntity.status(HttpStatus.SEE_OTHER)
              .location(URI.create(Links.act(found.eli(), Language.ENG)))
              .build();
      case NamedAct.NoAct _ -> page(frame, "Amendment viewer", HomeView.none(words, noAct(words)));
      case NamedAct.LookingUp _ ->
          page(
              frame,
              "Looking up " + name.number() + " · Amendment viewer",
              HomeView.lookingUp(
                  words,
                  "Looking up " + name.number() + " at the Publications Office…",
                  LOOK_UP + "?q=" + URLEncoder.encode(words, StandardCharsets.UTF_8),
                  LOOK_UP_EVENTS + "?q=" + URLEncoder.encode(words, StandardCharsets.UTF_8),
                  unanswered(words, name)));
      case NamedAct.Unanswered _ ->
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
              .contentType(Requests.HTML)
              .body(
                  templates
                      .catalogueHome(
                          frame,
                          "Amendment viewer",
                          HomeView.unanswered(words, unanswered(words, name)))
                      .render());
    };
  }

  private static HomeView.Unanswered unanswered(String words, NamedAct.Name name) {
    return new HomeView.Unanswered(Links.home(words, Search.Kind.ALL, 1), name.eurLex().toString());
  }

  /** Returns the line that says that no act has {@code words}, a CELEX number or an ELI. */
  private static String noAct(String words) {
    return "No act has the CELEX number or ELI " + words + ".";
  }

  /**
   * Returns what the reader typed, on one line. Thus a line break in it cannot start a log line of
   * its own.
   */
  private static String typed(String words) {
    return words.replaceAll("[\\r\\n]+", " ");
  }

  private static long millis(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }

  private ResponseEntity<String> page(Frame frame, String title, HomeView home) {
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .body(templates.catalogueHome(frame, title, home).render());
  }
}
