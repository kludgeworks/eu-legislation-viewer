// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * A version's page, at the ELI of the act and the date it applies on (any date: the version that
 * applies then), in a language by the EU's code, as an ELI has it: /eli/reg_impl/2015/1998/
 * 2026-03-22/eng. Asked by htmx (@HxRequest), only the text's part of it, which polls this URL
 * until the text is built and then loads it from the content module.
 */
@Controller
class VersionController {
  private static final String PATH =
      "/eli/{type}/{year}/{number}/{date:\\d{4}-\\d{2}-\\d{2}}/{language}";

  private final Acts acts;
  private final Texts texts;
  private final Templates templates;

  VersionController(Acts acts, Texts texts, Templates templates) {
    this.acts = acts;
    this.texts = texts;
    this.templates = templates;
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
        .varyBy("HX-Request")
        .contentType(MediaType.TEXT_HTML)
        .body(
            templates
                .catalogueVersion(page.act, page.version, page.language, page.eurLex(), text)
                .render());
  }

  /** The text's part of the version's page, as htmx polls for it. */
  @HxRequest
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
    final Act act;
    final Version version;
    final Language language;

    Page(String type, int year, String number, LocalDate date, String code) {
      language = language(code);
      act = acts.find(new Eli(type, year, number)).orElseThrow(VersionController::notFound);
      version = act.versionOn(date).orElseThrow(VersionController::notFound);
    }

    /** The text's part: polling this URL while the text is built, then loading it. */
    JteModel text(HttpServletRequest request) {
      return switch (texts.text(version, language)) {
        case Text.Ready ready -> templates.catalogueTextReady("/content/" + ready.root().hex());
        case Text.Building building ->
            templates.catalogueTextBuilding(building.status(), request.getRequestURI());
        case Text.Failed failed -> templates.catalogueTextFailed(failed.failure(), eurLex());
        case Text.Missing missing -> templates.catalogueTextMissing(version, eurLex());
      };
    }

    /** The version's text on EUR-Lex, in the language. */
    String eurLex() {
      return "https://eur-lex.europa.eu/legal-content/"
          + language.iso().toUpperCase(Locale.ROOT)
          + "/TXT/?uri=CELEX:"
          + version.celex();
    }
  }

  private static Language language(String code) {
    try {
      return Language.ofCode(code);
    } catch (IllegalArgumentException e) {
      throw notFound();
    }
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
