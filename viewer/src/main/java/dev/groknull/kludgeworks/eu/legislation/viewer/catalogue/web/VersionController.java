// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * A version's page, at the ELI of the act and the date it applies on (any date: the version that
 * applies then), in a language by the EU's code, as an ELI has it: /eli/reg_impl/2015/1998/
 * 2026-03-22/eng. Asked by htmx (@HxRequest), only the text's part of it, which asks this URL again
 * once the act is prepared in the language, every version of it, and then loads the version's text
 * from the content module, its outline beside it; but a boosted link of the page's (HX-Boosted)
 * gets the whole page, to swap its body in. The act's address without a date goes to the page of
 * the version that applies today, or of the date asked (on).
 */
@Controller
class VersionController {
  private static final String PATH =
      "/eli/{type}/{year}/{number}/{date:\\d{4}-\\d{2}-\\d{2}}/{language}";

  private final Acts acts;
  private final Texts texts;
  private final Content content;
  private final Templates templates;
  private final Clock clock;

  VersionController(Acts acts, Texts texts, Content content, Templates templates, Clock clock) {
    this.acts = acts;
    this.texts = texts;
    this.content = content;
    this.templates = templates;
    this.clock = clock;
  }

  /**
   * The act in a language, as Read links it: the page of the version that applies today, at its
   * first day, so that a bookmark of it keeps pointing at that version (the first version before
   * the act applies); or, as the Reading card's date form asks, the page for the date {@code on}.
   */
  @GetMapping(path = "/eli/{type}/{year}/{number}/{language}", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<Void> act(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate on) {
    var language = language(code);
    var eli = new Eli(type, year, number);
    var act = acts.find(eli).orElseThrow(VersionController::notFound);
    var date =
        on != null
            ? on
            : act.versionOn(today()).orElseGet(() -> act.timeline().versions().getFirst()).date();
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(Links.read(eli, date, language)))
        .build();
  }

  /** The version's page, its text's part as it stands. */
  @GetMapping(path = PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> page(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable("language") String code,
      HttpServletRequest request) {
    var page = new Page(type, year, number, date, code);
    var text = page.text(request);
    return ResponseEntity.ok()
        // htmx asks the same URL for the text's part (text below): caches keep the two apart. The
        // part's own response gets it from htmx-spring-boot, which sets it only on htmx's requests
        .varyBy("HX-Request", "HX-Boosted")
        .contentType(MediaType.TEXT_HTML)
        .body(
            templates
                .catalogueVersion(
                    Chrome.of(request),
                    page.version,
                    MastheadView.reading(page.act, page.version, date, page.language),
                    TimelineView.reading(page.act, page.version, page.language),
                    ReadingView.of(page.act, page.version, date, page.language, today()),
                    text)
                .render());
  }

  /** The text's part of the version's page, as htmx polls for it; not a boosted link's. */
  @HxRequest(boosted = false)
  @GetMapping(path = PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String text(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable("language") String code,
      HttpServletRequest request) {
    return new Page(type, year, number, date, code).text(request).render();
  }

  /** What a version's page shows: the act, the version that applies on the date, the language. */
  private final class Page {
    final Eli eli;
    final Act act;
    final Version version;
    final Language language;

    Page(String type, int year, String number, LocalDate date, String code) {
      language = language(code);
      eli = new Eli(type, year, number);
      act = acts.find(eli).orElseThrow(VersionController::notFound);
      version = act.versionOn(date).orElseThrow(VersionController::notFound);
    }

    /**
     * The text's part: polling this URL while the act is prepared in the language (every version of
     * it), then loading the version's text.
     */
    JteModel text(HttpServletRequest request) {
      var timeline = act.timeline().in(language);
      if (timeline.isEmpty()) {
        return templates.catalogueTextMissing(version, eurLex());
      }
      var preparation = texts.prepare(timeline.get(), language);
      if (!preparation.settled()) {
        return templates.catalogueTextBuilding(
            preparation, request.getRequestURI(), Links.prepare(eli, language));
      }
      var text = preparation.texts().get(version);
      if (text == null) {
        // a version not in the language
        return templates.catalogueTextMissing(version, eurLex());
      }
      return switch (text) {
        case Text.Ready ready ->
            templates.catalogueTextReady(
                "/content/" + ready.root().hex(),
                content.outline(ready.root()).orElseGet(() -> new Outline(List.of())));
        case Text.Building building ->
            templates.catalogueTextBuilding(
                preparation, request.getRequestURI(), Links.prepare(eli, language));
        case Text.Failed failed -> templates.catalogueTextFailed(failed.failure(), eurLex());
      };
    }

    /** The version's text on EUR-Lex, in the language. */
    String eurLex() {
      return Links.eurLex(version.celex(), language);
    }
  }

  /** Today, in Brussels. */
  private LocalDate today() {
    return Requests.today(clock);
  }

  private static Language language(String code) {
    return Requests.language(code);
  }

  private static ResponseStatusException notFound() {
    return Requests.notFound();
  }
}
