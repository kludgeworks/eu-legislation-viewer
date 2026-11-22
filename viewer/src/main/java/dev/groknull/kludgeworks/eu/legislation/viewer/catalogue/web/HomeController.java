// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HomeView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
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
@Controller
class HomeController {
  private static final Logger LOG = LoggerFactory.getLogger(HomeController.class);

  /** The address that looks up an act the catalogue does not have yet. */
  static final String LOOK_UP = "/lookup";

  /** The address of the stream that says when a lookup ends. */
  static final String LOOK_UP_EVENTS = LOOK_UP + "/events";

  /**
   * How long the lookup address waits for the lookup, for a page without JavaScript. After that,
   * the page says that the Publications Office did not answer.
   */
  private static final Duration WAIT = Duration.ofSeconds(60);

  /** How long a lookup's stream lasts before the page is asked to connect again. */
  private static final Duration STREAM = Duration.ofMinutes(2);

  /** How many acts a page of results has. */
  static final int PAGE = 20;

  /** How long the way back to a search's results stays in the site bar. */
  private static final Duration REMEMBERED = Duration.ofHours(2);

  /**
   * The CELEX number of an act, as a reader may type it. For example, {@code 32015R1998} or {@code
   * celex:32015r1998}.
   */
  private static final Pattern CELEX = Pattern.compile("(?:CELEX:)?(3\\d{4}[A-Z]\\d{4})");

  /**
   * The ELI of an act, as a reader may type or paste it. For example:
   *
   * <ul>
   *   <li>{@code http://data.europa.eu/eli/reg_impl/2015/1998/oj}
   *   <li>{@code eli/reg_impl/2015/1998}
   *   <li>{@code reg_impl/2015/1998}
   * </ul>
   */
  private static final Pattern ELI =
      Pattern.compile(
          "(?:https?://data\\.europa\\.eu/)?(?:eli/)?([a-z_]+)/(\\d{4})/(\\d+)(?:/oj)?(?:/[a-z]{3})?/?");

  private final Acts acts;
  private final Lookups lookups;
  private final Search search;
  private final Templates templates;

  HomeController(Acts acts, Lookups lookups, Search search, Templates templates) {
    this.acts = acts;
    this.lookups = lookups;
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
      HttpServletRequest request) {
    var kind = Search.Kind.of(type).orElseThrow(Requests::notFound);
    if (page < 1) {
      throw Requests.notFound();
    }
    var words = q.strip();
    var frame = Frame.of(request);
    if (words.isEmpty()) {
      return page(frame, "Amendment viewer", HomeView.empty());
    }
    var started = System.nanoTime();
    var straight = straight(words, frame, started, null, kind);
    if (straight.isPresent()) {
      return straight.get();
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
   * to it at once: it waits for the lookup, which runs in the background ({@link Lookups}). A
   * request from htmx gets the address of the act in {@code HX-Redirect}. If no act has it, the
   * response is the home page that says so. If the Publications Office does not answer, the
   * response is the home page that says so, with 503. Other words go to their results. A lookup is
   * logged when it starts and when it ends.
   */
  @GetMapping(path = LOOK_UP, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> lookUp(@RequestParam String q, HttpServletRequest request) {
    var words = q.strip();
    return straight(words, Frame.of(request), System.nanoTime(), request, Search.Kind.ALL)
        .orElseGet(
            () ->
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
    var name = named(words, Search.Kind.ALL).orElseThrow(Requests::notFound);
    var unanswered = unanswered(words, name);
    var emitter = new SseEmitter(STREAM.toMillis());
    name.lookUp(lookups)
        .whenComplete(
            (act, failure) -> {
              try {
                if (failure == null && act.isPresent()) {
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

  /** Returns what names one act in {@code words}, or empty if they are title words. */
  private Optional<Named> named(String words, Search.Kind kind) {
    var named = Named.of(words);
    if (named.isEmpty() && kind == Search.Kind.ALL) {
      named =
          search.identified(words).map(id -> new Named("indexed identifier", id.cited(), id, null));
    }
    return named;
  }

  private static HomeView.Unanswered unanswered(String words, Named name) {
    return new HomeView.Unanswered(Links.home(words, Search.Kind.ALL, 1), name.eurLex().toString());
  }

  /** Returns the line that says that no act has {@code words}, a CELEX number or an ELI. */
  private static String noAct(String words) {
    return "No act has the CELEX number or ELI " + words + ".";
  }

  /**
   * Returns the response when {@code words} identify one act. That is a redirect to the comparison
   * of the act, or the home page saying that no act has it. If the catalogue does not have the act
   * yet and {@code lookingUp} is null, it is the home page that says it looks the act up. Returns
   * none if the words are title words.
   *
   * @param lookingUp the request to {@link #LOOK_UP}, or null for a search
   */
  private Optional<ResponseEntity<String>> straight(
      String words,
      Frame frame,
      long started,
      @Nullable HttpServletRequest lookingUp,
      Search.Kind kind) {
    var named = named(words, kind);
    if (named.isEmpty()) {
      return Optional.empty();
    }
    var name = named.get();
    var act = name.catalogued(acts);
    var unanswered = unanswered(words, name);
    if (act.isEmpty() && lookingUp == null) {
      LOG.info(
          "Searched for “{}” by {}: not in the catalogue yet, so the page looks it up",
          typed(words),
          name.as());
      return Optional.of(
          page(
              frame,
              "Looking up " + name.number() + " · Amendment viewer",
              HomeView.lookingUp(
                  words,
                  "Looking up " + name.number() + " at the Publications Office…",
                  LOOK_UP + "?q=" + URLEncoder.encode(words, StandardCharsets.UTF_8),
                  LOOK_UP_EVENTS + "?q=" + URLEncoder.encode(words, StandardCharsets.UTF_8),
                  unanswered)));
    }
    if (act.isEmpty()) {
      LOG.info("Waiting for the lookup of “{}” by {}", typed(words), name.as());
      try {
        act = name.lookUp(lookups).get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
      } catch (ExecutionException | TimeoutException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        // The lookup logs its own failure, with its cause.
        LOG.info(
            "Looked up “{}” by {}: the Publications Office did not answer, in {} ms",
            typed(words),
            name.as(),
            millis(started));
        return Optional.of(
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(Requests.HTML)
                .body(
                    templates
                        .catalogueHome(
                            frame, "Amendment viewer", HomeView.unanswered(words, unanswered))
                        .render()));
      }
    }
    LOG.info(
        "{} “{}” by {}: {}, in {} ms",
        lookingUp == null ? "Searched for" : "Looked up",
        typed(words),
        name.as(),
        act.map(found -> found.id().celex() + (found.eli() == null ? ", which has no ELI" : ""))
            .orElse("no act has it"),
        millis(started));
    // An act without an ELI has no page here.
    var eli = act.map(Act::eli);
    if (eli.isEmpty()) {
      return Optional.of(page(frame, "Amendment viewer", HomeView.none(words, noAct(words))));
    }
    var location = Links.act(eli.get(), Language.ENG);
    return Optional.of(
        lookingUp != null && lookingUp.getHeader("HX-Request") != null
            ? ResponseEntity.ok().header("HX-Redirect", location).build()
            : ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(location)).build());
  }

  /**
   * What a reader typed that names one act: its CELEX number or its ELI.
   *
   * @param as how it names the act, for the log. For example, "CELEX number".
   * @param number the act's number, as a reader reads it. For example, "2015/1998" for a CELEX
   *     number, or "reg_impl/2015/1998" for an ELI.
   */
  private record Named(String as, String number, @Nullable ActIdentifier celex, @Nullable Eli eli) {

    /** Returns the act that {@code words} name, or empty if they are title words. */
    static Optional<Named> of(String words) {
      var celex = CELEX.matcher(words.toUpperCase(Locale.ROOT));
      if (celex.matches()) {
        var id = new ActIdentifier(celex.group(1));
        return Optional.of(new Named("CELEX number", id.cited(), id, null));
      }
      var path = ELI.matcher(words.toLowerCase(Locale.ROOT));
      if (path.matches()) {
        var eli = new Eli(path.group(1), Integer.parseInt(path.group(2)), path.group(3));
        return Optional.of(new Named("ELI", eli.path(), null, eli));
      }
      return Optional.empty();
    }

    Optional<Act> catalogued(Acts acts) {
      return celex != null ? acts.catalogued(celex) : acts.catalogued(Objects.requireNonNull(eli));
    }

    CompletableFuture<Optional<Act>> lookUp(Lookups lookups) {
      return celex != null ? lookups.lookUp(celex) : lookups.lookUp(Objects.requireNonNull(eli));
    }

    /** Returns the act on EUR-Lex, in English. */
    URI eurLex() {
      return celex != null
          ? celex.eurLex(Language.ENG)
          : Objects.requireNonNull(eli).eurLex(Language.ENG);
    }
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
