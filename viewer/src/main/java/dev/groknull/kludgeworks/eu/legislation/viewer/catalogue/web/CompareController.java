// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
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

/**
 * An act's comparison of two versions, in a language (Design: Mode: Compare):
 * /eli/reg_impl/2015/1998/eng/compare?from=2026-03-22&amp;to=2026-10-01, each end any date (the
 * version that applies then). Asked with dates that aren't the versions' first days, or with From
 * after To, or without them (the latest version against the one before), it goes to the address
 * that is. Asked by htmx (@HxRequest), only the changes' part, which polls this URL until both
 * texts are built, and then loads their Changes view from the comparison module, by their roots; a
 * boosted link of the page's gets the whole page.
 */
@Controller
class CompareController {
  private static final String PATH = "/eli/{type}/{year}/{number}/{language}/compare";

  private final Acts acts;
  private final Texts texts;
  private final Templates templates;
  private final Clock clock;

  CompareController(Acts acts, Texts texts, Templates templates, Clock clock) {
    this.acts = acts;
    this.texts = texts;
    this.templates = templates;
    this.clock = clock;
  }

  /** The comparison's page, its changes' part as it stands; or where its address is. */
  @GetMapping(path = PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> page(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to,
      HttpServletRequest request) {
    var page = new Page(new Eli(type, year, number), code, from, to);
    if (!page.at(from, to)) {
      return ResponseEntity.status(HttpStatus.SEE_OTHER)
          .location(URI.create(Links.compare(page.eli, page.from, page.to, page.language)))
          .build();
    }
    var today = Requests.today(clock);
    return ResponseEntity.ok()
        // htmx asks the same URL for the changes' part (changes below): caches keep them apart
        .varyBy("HX-Request", "HX-Boosted")
        .contentType(MediaType.TEXT_HTML)
        .body(
            templates
                .catalogueCompare(
                    Chrome.of(request),
                    page.from.celex() + " → " + page.to.celex(),
                    MastheadView.comparing(page.act, page.from, page.to, page.language),
                    TimelineView.comparing(page.act, page.from, page.to, page.language, today),
                    CompareView.of(page.act, page.from, page.to, page.language, today),
                    page.changes(request))
                .render());
  }

  /** The changes' part of the comparison's page, as htmx polls for it; not a boosted link's. */
  @HxRequest(boosted = false)
  @GetMapping(path = PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String changes(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      HttpServletRequest request) {
    return new Page(new Eli(type, year, number), code, from, to).changes(request).render();
  }

  /**
   * What a comparison's page shows: the act, the versions that apply on the dates asked in the
   * language, From the earlier; without dates, the latest and the one before it.
   */
  private final class Page {
    final Eli eli;
    final Language language;
    final Act act;
    final Version from;
    final Version to;

    Page(Eli eli, String code, @Nullable LocalDate from, @Nullable LocalDate to) {
      this.eli = eli;
      language = Requests.language(code);
      act = acts.find(eli).orElseThrow(Requests::notFound);
      var timeline = act.timeline().in(language).orElseThrow(Requests::notFound);
      var later =
          to == null
              ? timeline.latest()
              : timeline.applicableOn(to).orElseThrow(Requests::notFound);
      var earlier =
          from == null
              ? timeline.predecessor(later).orElse(later)
              : timeline.applicableOn(from).orElseThrow(Requests::notFound);
      var swapped = earlier.date().isAfter(later.date());
      this.from = swapped ? later : earlier;
      this.to = swapped ? earlier : later;
    }

    /** Whether {@code from} and {@code to} are this comparison's address: its versions' days. */
    boolean at(@Nullable LocalDate from, @Nullable LocalDate to) {
      return this.from.date().equals(from) && this.to.date().equals(to);
    }

    /**
     * The changes' part: why a text can't be compared, if either can't; polling this URL while
     * either is built; then the Changes view, loaded from the comparison module.
     */
    JteModel changes(HttpServletRequest request) {
      var earlier = texts.text(from, language);
      var later = texts.text(to, language);
      for (var each : new Text[] {earlier, later}) {
        var version = each == earlier ? from : to;
        var eurLex = Links.eurLex(version.celex(), language);
        if (each instanceof Text.Failed failed) {
          return templates.catalogueTextFailed(failed.failure(), eurLex);
        }
        if (each instanceof Text.Missing) {
          return templates.catalogueTextMissing(version, eurLex);
        }
      }
      for (var each : new Text[] {earlier, later}) {
        if (each instanceof Text.Building building) {
          return templates.catalogueTextBuilding(
              building.status(), request.getRequestURI() + "?" + request.getQueryString());
        }
      }
      return templates.catalogueCompareReady(
          "/comparison/"
              + ((Text.Ready) earlier).root().hex()
              + "/"
              + ((Text.Ready) later).root().hex());
    }
  }
}
