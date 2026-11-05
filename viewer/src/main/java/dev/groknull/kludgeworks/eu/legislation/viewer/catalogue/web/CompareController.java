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
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Wording;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
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

/**
 * An act's comparison of two versions, in a language (Design: Mode: Compare):
 * /eli/reg_impl/2015/1998/eng/compare?from=2026-03-22&amp;to=2026-10-01, each end any date (the
 * version that applies then). Asked with dates that aren't the versions' first days, or with From
 * after To, or without them (the latest version against the one before), it goes to the address
 * that is. The act is prepared whole in the language, every version of it, before the page shows
 * changes: until then it is the preparing page, which becomes this one in place; then the two
 * texts' Changes view loads from /changes beside it: the comparison module's, by their roots, each
 * difference with its sidenote, the acts that made it from the act's own facts.
 */
@Controller
class CompareController {
  private static final String PATH = "/eli/{type}/{year}/{number}/{language}/compare";

  private final Acts acts;
  private final Texts texts;
  private final Comparisons comparisons;
  private final Content content;
  private final Templates templates;
  private final LegalRenderer html;
  private final Clock clock;

  CompareController(
      Acts acts,
      Texts texts,
      Comparisons comparisons,
      Content content,
      Templates templates,
      LegalRenderer html,
      Clock clock) {
    this.acts = acts;
    this.texts = texts;
    this.comparisons = comparisons;
    this.content = content;
    this.templates = templates;
    this.html = html;
    this.clock = clock;
  }

  /**
   * The comparison's page, or where its address is: while the act is being prepared in the
   * language, the preparing page, which asks this address again once it is (prepared, for the
   * receipt saying what was).
   */
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
      @RequestParam(defaultValue = "false") boolean prepared,
      HttpServletRequest request) {
    var page = new Page(new Eli(type, year, number), code, from, to);
    if (!page.at(from, to)) {
      return ResponseEntity.status(HttpStatus.SEE_OTHER)
          .location(URI.create(Links.compare(page.eli, page.from, page.to, page.language)))
          .build();
    }
    var preparation = texts.prepare(page.act, page.language);
    var title = page.from.celex() + " → " + page.to.celex();
    var chrome = Chrome.of(request);
    var masthead = MastheadView.comparing(page.act, page.from, page.to, page.language);
    if (preparation.preparing()) {
      return ResponseEntity.ok()
          .contentType(MediaType.TEXT_HTML)
          .body(
              templates
                  .cataloguePreparing(
                      chrome,
                      title,
                      masthead.whilePreparing(),
                      TimelineView.preparing(page.act, preparation),
                      PreparingView.of(
                          page.act,
                          preparation,
                          "You asked for a comparison of the version that applies on "
                              + Wording.date(page.from.date())
                              + " with the one that applies on "
                              + Wording.date(page.to.date()),
                          self(request),
                          clock.instant()))
                  .render());
    }
    var today = Requests.today(clock);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(
            templates
                .catalogueCompare(
                    chrome,
                    title,
                    masthead,
                    TimelineView.comparing(
                        page.act,
                        page.from,
                        page.to,
                        page.language,
                        today,
                        TimelineView.states(preparation)),
                    CompareView.of(page.act, page.from, page.to, page.language, today),
                    page.changes(preparation, self(request)),
                    prepared ? PreparingView.receipt(preparation, page.language) : null,
                    ArrivingView.of(page.act, preparation, self(request)).orElse(null))
                .render());
  }

  /**
   * The comparison's Changes view, as its page loads it once both texts are built: the comparison
   * module's, worked out now if no one has yet, beside the later text's outline, each difference
   * with its sidenote; 404 at an address that isn't the comparison's, or before both texts are.
   */
  @GetMapping(path = PATH + "/changes", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String changes(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    var page = new Page(new Eli(type, year, number), code, from, to);
    if (!page.at(from, to)) {
      throw Requests.notFound();
    }
    var preparation = texts.preparation(page.act, page.language);
    if (!(preparation.texts().get(page.from) instanceof Text.Ready earlier)
        || !(preparation.texts().get(page.to) instanceof Text.Ready later)) {
      throw Requests.notFound();
    }
    var comparison =
        comparisons.between(earlier.root(), later.root()).orElseThrow(Requests::notFound);
    var changes = ChangesView.of(comparison);
    var sidenotes = new HashMap<Integer, SidenoteView>();
    for (var passage : changes.passages()) {
      if (passage instanceof ChangesView.Shown shown) {
        sidenotes.put(
            shown.number(),
            SidenoteView.of(page.act, page.from, page.to, shown.difference(), page.language));
      }
    }
    var outline = content.outline(later.root()).orElseGet(() -> new Outline(List.of()));
    return templates
        .catalogueChanges(
            changes,
            OutlineView.of(outline, comparison.differences()),
            sidenotes,
            html,
            "/content/" + later.root().hex() + "/units")
        .render();
  }

  /**
   * Try again, as the failure panel posts it to the comparison's page: the act's texts that failed
   * where trying again may help, tried again; then the page, preparing once more.
   */
  @PostMapping(path = PATH)
  ResponseEntity<Void> retry(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      HttpServletRequest request) {
    var page = new Page(new Eli(type, year, number), code, from, to);
    texts.retry(page.act, page.language);
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(Links.compare(page.eli, page.from, page.to, page.language)))
        .build();
  }

  /** The page's address, with its query: /eli/reg_impl/2015/1998/eng/compare?from=…&amp;to=…. */
  private static String self(HttpServletRequest request) {
    return request.getRequestURI() + "?" + request.getQueryString();
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
     * The changes, once the act is prepared in the language: why a text can't be compared, if
     * either can't, with Try again posting to {@code self}; why the act couldn't be, if none of its
     * texts could; that either is being prepared, if it appeared since; else the Changes view,
     * loaded from beside the page.
     */
    JteModel changes(Preparation preparation, String self) {
      if (preparation.ready() == 0) {
        return templates.catalogueTextFailed(FailureView.act(act, preparation, self));
      }
      // both in the language, as the comparison's versions are picked from its timeline
      var earlier = Objects.requireNonNull(preparation.texts().get(from));
      var later = Objects.requireNonNull(preparation.texts().get(to));
      for (var each : new Text[] {earlier, later}) {
        if (each instanceof Text.Failed failed) {
          return templates.catalogueTextFailed(
              FailureView.comparing(
                  act, from, to, each == earlier ? from : to, failed.failure(), language, self));
        }
      }
      if (earlier instanceof Text.Ready && later instanceof Text.Ready) {
        return templates.catalogueCompareReady(Links.changes(eli, from, to, language));
      }
      // either end a version that appeared since, being prepared
      return templates.catalogueTextArriving();
    }
  }
}
