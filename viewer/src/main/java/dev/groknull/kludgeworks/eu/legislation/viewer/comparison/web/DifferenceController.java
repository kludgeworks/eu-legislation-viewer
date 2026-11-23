// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Alternatives;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributions;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.RowFolds;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Release;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Serves one difference of the comparison of two versions of an act, or the differences at a place,
 * for the compare page and for the page of an amending act.
 *
 * <p>The pages have slots that htmx fills from the addresses here, each the address of the compare
 * page with a segment added and its query, as the catalogue's {@link ComparePage} writes them:
 *
 * <ul>
 *   <li>{@code /difference}: one difference by the eId of its occurrence, or some rows of one of
 *       its tables. The views load it when the reader opens an annex inserted whole or a fold of
 *       rows.
 *   <li>{@code /place}: the differences at some occurrences. The page of an amending act loads the
 *       wording of one of its changes from here.
 *   <li>{@code /either}: the differences that an act or another act of its version made, where the
 *       data does not say which.
 * </ul>
 *
 * <p>The fragments answer only the canonical address of a comparison, as the page links to it: the
 * first days of two versions in the language, the earlier first, and an act between them, if any.
 * Anything else is 404. {@link ComparisonViewsController} serves the whole comparison.
 */
@Controller
class DifferenceController {
  private static final Logger LOG = LoggerFactory.getLogger(DifferenceController.class);

  /** The rows of a fold, as its address gives them. For example, {@code 10-40}. */
  private static final Pattern ROWS = Pattern.compile("(\\d{1,9})-(\\d{1,9})");

  /**
   * The list of the changes that one of two acts made shows the wording of each change when it
   * opens, up to this many changes. The page of an amending act has the same limit for its own
   * changes.
   */
  private static final int OPEN = 25;

  private final Comparisons comparisons;
  private final Attributions attributions;
  private final Content content;
  private final Templates templates;
  private final LegalRenderer html;
  private final Fragments fragments;

  DifferenceController(
      Acts acts,
      Texts texts,
      Comparisons comparisons,
      Attributions attributions,
      Content content,
      Templates templates,
      LegalRenderer html,
      Release release) {
    this.comparisons = comparisons;
    this.attributions = attributions;
    this.content = content;
    this.templates = templates;
    this.html = html;
    this.fragments = new Fragments(acts, texts, release, html);
  }

  /**
   * Returns one difference of the comparison, the one at the occurrence {@code eId}, as the views
   * show it. With {@code table} and {@code rows}, it returns only those rows of that table of the
   * occurrence, as they read ({@link RowFolds}). The views load it when the reader opens an annex
   * inserted whole or a fold of rows, so that a long comparison does not send them until they are
   * asked for.
   *
   * <p>If the comparison is limited to the changes of one act, the difference is that of the act's
   * own comparison, as for the differences.
   *
   * <p>Returns 404 if the address is not the canonical address of a comparison, if the texts are
   * not built yet, if no difference is at {@code eId}, or if the rows are not in the table.
   *
   * <p>Once the address names its two texts, the texts, the version of the differ and the release
   * decide the response. They are its entity tag, and a browser that has the response gets 304,
   * with nothing read or rendered. The browser asks each time ({@link Release#REVALIDATED}): a
   * version's text can be replaced, so the same address can come to name other texts.
   */
  @GetMapping(path = ComparePage.DIFFERENCE_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  @Nullable String difference(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(ComparePage.FROM) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(ComparePage.TO) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(name = ComparePage.ACT, required = false) @Nullable String act,
      @RequestParam(ComparePage.EID) String eId,
      @RequestParam(name = ComparePage.TABLE, required = false) @Nullable Integer table,
      @RequestParam(name = ComparePage.ROWS, required = false) @Nullable String rows,
      @RequestParam(name = ComparePage.OUTLINE, defaultValue = "false") boolean outline,
      HttpServletRequest request,
      HttpServletResponse response) {
    var ends = fragments.ends(new Eli(type, year, number), code, from, to, act);
    var page =
        ComparePage.ofFragment(
            request.getRequestURI(), from, to, ends.only == null ? null : ends.only.celex(), null);
    var started = System.nanoTime();
    var preparation = ends.preparation;
    var only = ends.only;
    var pair = Fragments.ready(ends, preparation);
    var earlier = pair.earlier();
    var later = pair.later();
    if (fragments.notModified(request, response, pair)) {
      return null;
    }
    if (outline) {
      var entry =
          content
              .outline(pair.after())
              .flatMap(each -> entry(each.entries(), eId))
              .orElseThrow(Requests::notFound);
      return templates.comparisonAnnexoutline(entry.entries(), page).render();
    }
    var difference =
        comparisons.difference(pair.before(), pair.after(), eId).orElseThrow(Requests::notFound);
    if (table == null && rows == null) {
      var html = fragments.render(new DifferencesView.Shown(0, difference, "").folded(page));
      LOG.info(
          "Difference of {} from {} to {} in {}{} at {}, in {} ms",
          ends.act.id(),
          earlier.date(),
          later.date(),
          ends.language.code(),
          only == null ? "" : " by " + only,
          difference.eId(),
          Duration.ofNanos(System.nanoTime() - started).toMillis());
      return html;
    }
    var span = rows == null ? null : ROWS.matcher(rows);
    if (table == null || span == null || !span.matches()) {
      throw Requests.notFound();
    }
    var first = Integer.parseInt(span.group(1));
    var last = Integer.parseInt(span.group(2));
    var found = RowFolds.rows(difference.shown(), table, first, last);
    if (found.isEmpty()) {
      throw Requests.notFound();
    }
    var html = new StringBuilder();
    found.forEach(row -> html.append(fragments.render(row)));
    LOG.info(
        "Rows of {} from {} to {} in {}{} at {}: {} rows, in {} ms",
        ends.act.id(),
        earlier.date(),
        later.date(),
        ends.language.code(),
        only == null ? "" : " by " + only,
        difference.eId(),
        found.size(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return html.toString();
  }

  /**
   * Returns the differences at the occurrences {@code eIds} of the comparison, as the views show
   * them ({@link Comparison#at}). The page of an amending act loads the wording of one of its
   * changes from here. If the occurrences are the same in both texts, it says so.
   *
   * <p>With {@code sides}, it returns the wording on each of the two days of the Compare page, side
   * by side. The Compare page of one provision loads it. {@code fromDay} and {@code toDay} are the
   * two days, where they are not the first days of the two versions.
   *
   * <p>Returns 404 if the address is not the canonical address of a comparison, or if the texts are
   * not built yet. The entity tag is as for one difference.
   */
  @GetMapping(path = ComparePage.PLACE_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  @Nullable String place(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(ComparePage.FROM) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(ComparePage.TO) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(name = ComparePage.ACT, required = false) @Nullable String act,
      @RequestParam(ComparePage.EID) List<String> eIds,
      @RequestParam(name = ComparePage.SIDES, defaultValue = "false") boolean sides,
      @RequestParam(name = ComparePage.FROM_DAY, required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate fromDay,
      @RequestParam(name = ComparePage.TO_DAY, required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate toDay,
      HttpServletRequest request,
      HttpServletResponse response) {
    var ends = fragments.ends(new Eli(type, year, number), code, from, to, act);
    var page =
        ComparePage.ofFragment(
                request.getRequestURI(),
                from,
                to,
                ends.only == null ? null : ends.only.celex(),
                null)
            .on(fromDay, toDay);
    var started = System.nanoTime();
    var preparation = ends.preparation;
    var only = ends.only;
    var pair = Fragments.ready(ends, preparation);
    var earlier = pair.earlier();
    var later = pair.later();
    if (fragments.notModified(request, response, pair)) {
      return null;
    }
    var found =
        comparisons.between(pair.before(), pair.after()).orElseThrow(Requests::notFound).at(eIds);
    String lead = null;
    if (found.isEmpty() && only != null) {
      // A chapter that the annex keeps flat has no difference of its own.
      var attributed =
          attributions
              .of(ends.act, ends.versions(), earlier, later, null)
              .orElseThrow(Requests::notFound);
      found = Attribution.madeIn(attributed.each(), eIds, only);
      lead =
          found.isEmpty()
              ? null
              : "The text tags these changes in this part of the annex with " + only.cited() + ".";
    }
    var shown =
        found.stream().map(difference -> new DifferencesView.Shown(0, difference, "")).toList();
    var rendered =
        sides
            ? templates
                .comparisonSides(
                    shown, html, page, Dates.inWords(page.earlier()), Dates.inWords(page.later()))
                .render()
            : templates.comparisonPlace(shown, html, page, lead).render();
    LOG.info(
        "Place of {} from {} to {} in {}{} at {} occurrences: {} differences, in {} ms",
        ends.act.id(),
        earlier.date(),
        later.date(),
        ends.language.code(),
        only == null ? "" : " by " + only,
        eIds.size(),
        shown.size(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return rendered;
  }

  /**
   * Returns the differences that the act {@code act} or another act of its version made, where the
   * data does not say which. These are the differences with no tag of an act, in a version that
   * integrates two acts or more. The page of an amending act shows them in a separate list, under
   * "By this act or by …". If there are none, the fragment is empty.
   *
   * <p>Returns 404 if the address is not the canonical address of a comparison that is limited to
   * one act, or if the texts are not built yet.
   */
  @GetMapping(path = ComparePage.EITHER_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String either(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(ComparePage.FROM) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(ComparePage.TO) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(ComparePage.ACT) String act,
      HttpServletRequest request) {
    var ends = fragments.ends(new Eli(type, year, number), code, from, to, act);
    var only = Objects.requireNonNull(ends.only);
    var page = ComparePage.ofFragment(request.getRequestURI(), from, to, only.celex(), null);
    var started = System.nanoTime();
    var inclusion = ends.inclusion(only).orElseThrow(Requests::notFound);
    var attributed =
        attributions
            .of(ends.act, ends.versions(), inclusion.before(), inclusion.first(), null)
            .orElseThrow(Requests::notFound);
    var alternatives = Alternatives.of(only, attributed.each());
    var others = alternatives.others();
    var shown =
        alternatives.differences().stream()
            .map(difference -> new DifferencesView.Shown(0, difference, ""))
            .toList();
    var rendered =
        templates
            .comparisonEither(
                DifferencesView.either(others.stream().map(ends.act::cited).toList()),
                shown,
                shown.size() <= OPEN,
                html,
                page)
            .render();
    LOG.info(
        "Differences of {} from {} to {} in {} by {} or another act: {}, in {} ms",
        ends.act.id(),
        inclusion.before().date(),
        inclusion.first().date(),
        ends.language.code(),
        only,
        shown.size(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return rendered;
  }

  /** Returns the entry of the occurrence {@code eId} in {@code entries}, at any depth. */
  private static Optional<Outline.Entry> entry(List<Outline.Entry> entries, String eId) {
    for (var each : entries) {
      if (each.eId().equals(eId)) {
        return Optional.of(each);
      }
      var below = entry(each.entries(), eId);
      if (below.isPresent()) {
        return below;
      }
    }
    return Optional.empty();
  }
}
