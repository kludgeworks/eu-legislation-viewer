// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextMarks;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.AmendingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HistoryView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Shows the history of an act, and the page of one of its amending acts.
 *
 * <p>The history lists the acts that amend the act, newest first, with the state of each on a day.
 * For example, {@code /eli/reg/2018/1862/eng/history?on=2022-08-01}. The page of an amending act
 * lists what that act changed, with the state of each change on the day. For example, {@code
 * /eli/reg/2018/1862/eng/history/32022R1190?on=2022-08-01}.
 *
 * <p>The day is today, unless the address gives one in {@code on}. Both pages need only the
 * catalogue. They do not wait for the texts of the act. The page of an amending act puts its
 * changes in the order of the text if a text of the act is built.
 */
@Controller
class HistoryController {
  private static final String HISTORY = "/eli/{type}/{year}/{number}/{language}/history";

  private final Catalogued acts;
  private final Texts texts;
  private final Templates templates;
  private final Clock clock;

  HistoryController(Catalogued acts, Texts texts, Templates templates, Clock clock) {
    this.acts = acts;
    this.texts = texts;
    this.templates = templates;
    this.clock = clock;
  }

  /**
   * Returns the history of the act on the day {@code on}. If {@code from} and {@code to} are given,
   * the history is of these two days: it shows the amending acts between them, and their state on
   * the later day.
   */
  @GetMapping(path = HISTORY, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String history(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate on,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to,
      HttpServletRequest request) {
    var language = Requests.language(code);
    var act = act(type, year, number);
    var today = Timeline.today(clock);
    LocalDate first = null;
    var day = on != null ? on : today;
    if (from != null && to != null && !from.equals(to)) {
      first = from.isBefore(to) ? from : to;
      day = from.isBefore(to) ? to : from;
    }
    Map<Version, TextMarks> marks =
        first == null ? Map.of() : texts.marks(texts.preparation(act, language));
    return templates
        .catalogueHistory(
            Frame.of(request),
            MastheadView.history(act, day, language),
            HistoryView.of(act, day, today, language, first, marks))
        .render();
  }

  /**
   * Returns the page of the amending act {@code celex} on the day {@code on}. If {@code applies} is
   * given, the page shows only the changes that apply from that day.
   */
  @GetMapping(path = HISTORY + "/{celex}", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String amending(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @PathVariable String celex,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate on,
      @RequestParam(required = false) @Nullable String applies,
      HttpServletRequest request) {
    var language = Requests.language(code);
    var act = act(type, year, number);
    if (!ActIdentifier.isCelex(celex)) {
      throw Requests.notFound();
    }
    var amending = new ActIdentifier(celex);
    if (!act.amendingActs(language).contains(amending)) {
      throw Requests.notFound();
    }
    var today = Timeline.today(clock);
    var day = on != null ? on : today;
    var marks = texts.marks(texts.preparation(act, language));
    return templates
        .catalogueAmending(
            Frame.of(request),
            MastheadView.history(act, day, language),
            AmendingView.of(
                act,
                amending,
                day,
                today,
                language,
                applies,
                latest(act, language, marks),
                step(act, amending, language, marks)))
        .render();
  }

  private Act act(String type, int year, String number) {
    return acts.act(new Eli(type, year, number));
  }

  /**
   * Returns the marks of the latest text of {@code act} in {@code language} that is built, or null
   * if none is built. {@code marks} are the marks of the texts that are built.
   */
  private static @Nullable TextMarks latest(
      Act act, Language language, Map<Version, TextMarks> marks) {
    return act.timeline(language).map(Timeline::versions).orElseGet(List::of).reversed().stream()
        .map(marks::get)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns the two texts that show the wording of the changes of {@code amending}: the text of the
   * first version that integrates it, and the text of the version before. Returns null if no
   * version in {@code language} integrates it, or if one of the two texts is not built.
   */
  private static AmendingView.@Nullable Step step(
      Act act, ActIdentifier amending, Language language, Map<Version, TextMarks> marks) {
    var later = act.integrating(amending, language).orElse(null);
    var earlier =
        later == null
            ? null
            : act.timeline(language).flatMap(each -> each.predecessor(later)).orElse(null);
    if (later == null || earlier == null) {
      return null;
    }
    var before = marks.get(earlier);
    var after = marks.get(later);
    return before == null || after == null
        ? null
        : new AmendingView.Step(earlier.date(), later.date(), before.eIds(), after.eIds());
  }

  /** Answers a page whose act the catalogue does not have yet with the page that waits for it. */
  @ExceptionHandler(NotCatalogued.class)
  ResponseEntity<String> notCatalogued(NotCatalogued notCatalogued, HttpServletRequest request) {
    return ActLookupPages.lookingUp(templates, notCatalogued, request);
  }

  /** Answers a page whose address no act has with the page that says so, with 404. */
  @ExceptionHandler(NoSuchAct.class)
  ResponseEntity<String> missing(NoSuchAct missing, HttpServletRequest request) {
    return ActLookupPages.missing(templates, missing, request);
  }
}
