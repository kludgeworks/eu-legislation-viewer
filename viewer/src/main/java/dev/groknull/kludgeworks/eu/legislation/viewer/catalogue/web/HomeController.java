// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.chrome.Requests;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HomeView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
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

/**
 * The home page. It has one box, Find an act.
 *
 * <ul>
 *   <li>A CELEX number or an ELI goes directly to the comparison page of the act. The comparison is
 *       the first page of an act that a search opens.
 *   <li>Title words list the acts that have the words in their English title. The list shows one
 *       page at a time, with the closest match first. The link of a filter limits the list to a
 *       kind of act. For example, {@code ?type=regulation}.
 * </ul>
 *
 * <p>The search is kept in the {@link Chrome#SEARCH} cookie for a while. The site bar uses it to
 * link back to the results.
 *
 * <p>Search is a placeholder for now. Each search is logged once, at INFO. The log line has what
 * the reader typed, how it was read, the kind, the page, what it found and how long it took. Real
 * use can then show how to change the ranking, the languages and the act numbers that readers type.
 */
@Controller
class HomeController {
  private static final Logger LOG = LoggerFactory.getLogger(HomeController.class);

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
  private final Search search;
  private final Templates templates;

  HomeController(Acts acts, Search search, Templates templates) {
    this.acts = acts;
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
    var chrome = Chrome.of(request);
    if (words.isEmpty()) {
      return page(chrome, "Amendment viewer", HomeView.empty());
    }
    var started = System.nanoTime();
    var straight = straight(words, chrome, started);
    if (straight.isPresent()) {
      return straight.get();
    }
    var query = Search.Query.of(words, kind, (page - 1) * PAGE, PAGE);
    if (query.isEmpty()) {
      LOG.info("Searched for “{}”: no words to search by", typed(words));
      return page(
          chrome, "Amendment viewer", HomeView.none(words, "Type words of an act's title."));
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
                Chrome.SEARCH,
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
                    chrome,
                    "Acts matching “" + words + "” · Amendment viewer",
                    HomeView.found(words, kind, page, PAGE, found))
                .render());
  }

  /**
   * Returns the response for {@code words} if they are a CELEX number or an ELI. That is a redirect
   * to the comparison of the act, or the home page saying that no act has it. Returns none if the
   * words are title words.
   */
  private Optional<ResponseEntity<String>> straight(String words, Chrome chrome, long started) {
    var celex = CELEX.matcher(words.toUpperCase(Locale.ROOT));
    var path = ELI.matcher(words.toLowerCase(Locale.ROOT));
    Optional<Act> act;
    String as;
    if (celex.matches()) {
      act = acts.find(new ActIdentifier(celex.group(1)));
      as = "CELEX number";
    } else if (path.matches()) {
      act = acts.find(new Eli(path.group(1), Integer.parseInt(path.group(2)), path.group(3)));
      as = "ELI";
    } else {
      return Optional.empty();
    }
    LOG.info(
        "Searched for “{}” by {}: {}, in {} ms",
        typed(words),
        as,
        act.map(found -> found.id().celex() + (found.eli() == null ? ", which has no ELI" : ""))
            .orElse("no act has it"),
        millis(started));
    return Optional.of(
        // An act without an ELI has no page here.
        act.map(Act::eli)
            .map(
                eli ->
                    ResponseEntity.status(HttpStatus.SEE_OTHER)
                        .location(URI.create(Links.compare(eli, Language.ENG)))
                        .<String>build())
            .orElseGet(
                () ->
                    page(
                        chrome,
                        "Amendment viewer",
                        HomeView.none(
                            words, "No act has the CELEX number or ELI " + words + "."))));
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

  private ResponseEntity<String> page(Chrome chrome, String title, HomeView home) {
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .body(templates.catalogueHome(chrome, title, home).render());
  }
}
