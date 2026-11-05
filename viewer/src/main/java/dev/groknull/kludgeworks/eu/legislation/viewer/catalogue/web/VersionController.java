// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Wording;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * A version's page, at the ELI of the act and the date it applies on (any date: the version that
 * applies then), in a language by the EU's code, as an ELI has it: /eli/reg_impl/2015/1998/
 * 2026-03-22/eng. The act is prepared whole in the language, every version of it, before the page
 * shows a text: until then it is the preparing page, which becomes this one in place; then the
 * version's text loads from the content module, its outline beside it, or why it couldn't be made
 * is in its place, with Try again posting to the page where that may help. The act's address
 * without a date goes to the page of the version that applies today, or of the date asked (on).
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

  /**
   * The version's page: while the act is being prepared in the language, the preparing page, which
   * asks this address again once it is (prepared, for the receipt saying what was).
   */
  @GetMapping(path = PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String page(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable("language") String code,
      @RequestParam(defaultValue = "false") boolean prepared,
      HttpServletRequest request) {
    var page = new Page(type, year, number, date, code);
    var chrome = Chrome.of(request);
    var masthead = MastheadView.reading(page.act, page.version, date, page.language);
    if (page.act.timeline().in(page.language).isEmpty()) {
      return page.render(
          chrome,
          masthead,
          templates.catalogueTextMissing(page.version, page.eurLex()),
          null,
          List.of());
    }
    var preparation = texts.prepare(page.act, page.language);
    if (!preparation.settled()) {
      return templates
          .cataloguePreparing(
              chrome,
              page.version.celex(),
              masthead.whilePreparing(),
              TimelineView.preparing(page.act, preparation),
              PreparingView.of(
                  page.act,
                  preparation,
                  "You asked for the version that applies on " + Wording.date(date),
                  request.getRequestURI(),
                  clock.instant()))
          .render();
    }
    return page.render(
        chrome,
        masthead,
        page.text(preparation, request.getRequestURI()),
        prepared ? PreparingView.receipt(preparation, page.language) : null,
        preparation.failedVersions());
  }

  /**
   * Try again, as the failure panel posts it to the version's page: the act's texts that failed
   * where trying again may help, tried again; then the page, preparing once more.
   */
  @PostMapping(path = PATH)
  ResponseEntity<Void> retry(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable("language") String code,
      HttpServletRequest request) {
    var page = new Page(type, year, number, date, code);
    texts.retry(page.act, page.language);
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(request.getRequestURI()))
        .build();
  }

  /** What a version's page shows: the act, the version that applies on the date, the language. */
  private final class Page {
    final Eli eli;
    final Act act;
    final Version version;
    final LocalDate date;
    final Language language;

    Page(String type, int year, String number, LocalDate date, String code) {
      language = language(code);
      eli = new Eli(type, year, number);
      act = acts.find(eli).orElseThrow(VersionController::notFound);
      version = act.versionOn(date).orElseThrow(VersionController::notFound);
      this.date = date;
    }

    String render(
        Chrome chrome,
        MastheadView masthead,
        JteModel text,
        @Nullable String receipt,
        List<Version> failed) {
      return templates
          .catalogueVersion(
              chrome,
              version,
              masthead,
              TimelineView.reading(act, version, language, failed),
              ReadingView.of(act, version, date, language, today()),
              text,
              receipt)
          .render();
    }

    /**
     * The version's text, once the act is prepared: built, or why it couldn't be, with Try again
     * posting to {@code self}; why the act couldn't be, if none of its texts could.
     */
    JteModel text(Preparation preparation, String self) {
      var text = preparation.texts().get(version);
      if (text == null) {
        // a version not in the language
        return templates.catalogueTextMissing(version, eurLex());
      }
      if (preparation.ready() == 0) {
        return templates.catalogueTextFailed(FailureView.act(act, preparation, self));
      }
      return switch (text) {
        case Text.Ready ready ->
            templates.catalogueTextReady(
                "/content/" + ready.root().hex(),
                content.outline(ready.root()).orElseGet(() -> new Outline(List.of())));
        case Text.Queued _, Text.Building _ ->
            throw new IllegalStateException("A settled preparation has a text not yet built");
        case Text.Failed failed ->
            templates.catalogueTextFailed(
                FailureView.reading(act, version, failed.failure(), language, self));
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
