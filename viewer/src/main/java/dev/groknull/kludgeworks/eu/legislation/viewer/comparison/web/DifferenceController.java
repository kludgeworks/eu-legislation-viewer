// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActAddress;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.ComparedTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.RowFolds;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.TheChangesOfOneAct;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.ModelAttribute;
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
/*
 * A handler takes the act and the language of its address as an ActAddress, which the catalogue
 * gives. The check knows only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
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

  private final ComparedTexts compared;
  private final Comparisons comparisons;
  private final Content content;
  private final Templates templates;
  private final LegalRenderer html;
  private final Fragments fragments;

  DifferenceController(
      ComparedTexts compared,
      Comparisons comparisons,
      Content content,
      Templates templates,
      LegalRenderer html,
      Release release) {
    this.compared = compared;
    this.comparisons = comparisons;
    this.content = content;
    this.templates = templates;
    this.html = html;
    this.fragments = new Fragments(compared, release, html);
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
      ActAddress address,
      @Valid @ModelAttribute DifferenceRequest asked,
      HttpServletRequest request,
      HttpServletResponse response) {
    var from = asked.from();
    var to = asked.to();
    var eId = asked.eId();
    var table = asked.table();
    var rows = asked.rows();
    var outline = Boolean.TRUE.equals(asked.outline());
    var ends = fragments.ends(address, from, to, asked.act());
    var page =
        ComparePage.ofFragment(
            request.getRequestURI(),
            from,
            to,
            ends.only() == null ? null : ends.only().celex(),
            null);
    var started = System.nanoTime();
    var only = ends.only();
    var pair = Fragments.ready(ends);
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
          ends.act().id(),
          earlier.date(),
          later.date(),
          ends.language().code(),
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
        ends.act().id(),
        earlier.date(),
        later.date(),
        ends.language().code(),
        only == null ? "" : " by " + only,
        difference.eId(),
        found.size(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return html.toString();
  }

  /**
   * Returns the differences at the occurrences {@code eIds} of the comparison, as the views show
   * them ({@link TheChangesOfOneAct#differencesAtAPlace}). The page of an amending act loads the
   * wording of one of its changes from here. If the occurrences are the same in both texts, it says
   * so.
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
      ActAddress address,
      @Valid @ModelAttribute PlaceRequest asked,
      HttpServletRequest request,
      HttpServletResponse response) {
    var from = asked.from();
    var to = asked.to();
    var eIds = asked.eId();
    var sides = Boolean.TRUE.equals(asked.sides());
    var fromDay = asked.fromDay();
    var toDay = asked.toDay();
    var ends = fragments.ends(address, from, to, asked.act());
    var page =
        ComparePage.ofFragment(
                request.getRequestURI(),
                from,
                to,
                ends.only() == null ? null : ends.only().celex(),
                null)
            .on(fromDay, toDay);
    var started = System.nanoTime();
    var only = ends.only();
    var pair = Fragments.ready(ends);
    var earlier = pair.earlier();
    var later = pair.later();
    if (fragments.notModified(request, response, pair)) {
      return null;
    }
    var found = compared.at(ends, eIds).orElseThrow(Requests::notFound);
    var taggedBy = found.taggedBy();
    var lead =
        taggedBy == null
            ? null
            : "The text tags these changes in this part of the annex with "
                + taggedBy.cited()
                + ".";
    var shown =
        found.differences().stream()
            .map(difference -> new DifferencesView.Shown(0, difference, ""))
            .toList();
    var rendered =
        sides
            ? templates
                .comparisonSides(
                    shown, html, page, Dates.inWords(page.earlier()), Dates.inWords(page.later()))
                .render()
            : templates.comparisonPlace(shown, html, page, lead).render();
    LOG.info(
        "Place of {} from {} to {} in {}{} at {} occurrences: {} differences, in {} ms",
        ends.act().id(),
        earlier.date(),
        later.date(),
        ends.language().code(),
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
      ActAddress address, @Valid @ModelAttribute EitherRequest asked, HttpServletRequest request) {
    var from = asked.from();
    var to = asked.to();
    var ends = fragments.ends(address, from, to, asked.act());
    var only = Objects.requireNonNull(ends.only());
    var page = ComparePage.ofFragment(request.getRequestURI(), from, to, only.celex(), null);
    var started = System.nanoTime();
    var alternatives = compared.either(ends).orElseThrow(Requests::notFound);
    var others = alternatives.others();
    var shown =
        alternatives.differences().stream()
            .map(difference -> new DifferencesView.Shown(0, difference, ""))
            .toList();
    var rendered =
        templates
            .comparisonEither(
                DifferencesView.either(others.stream().map(ends.act()::cited).toList()),
                shown,
                shown.size() <= OPEN,
                html,
                page)
            .render();
    LOG.info(
        "Differences of {} from {} to {} in {} by {} or another act: {}, in {} ms",
        ends.act().id(),
        ends.earlier().date(),
        ends.later().date(),
        ends.language().code(),
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

  /**
   * What the address of one difference asks for, beside the act and the language. The names are
   * those that {@link ComparePage} writes.
   *
   * @param from the first day of the earlier version
   * @param to the first day of the later version
   * @param act the CELEX number of the act that the comparison is limited to, or null
   * @param eId the eId of the occurrence of the difference
   * @param table the number of the table whose rows the address asks for, or null
   * @param rows the rows of the table, or null. For example, {@code 10-40}.
   * @param outline true if the answer has the outline of the difference, or null
   */
  record DifferenceRequest(
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @Nullable String act,
      @NotNull String eId,
      @Nullable Integer table,
      @Nullable String rows,
      @Nullable Boolean outline) {}

  /**
   * What the address of the differences at a place asks for, beside the act and the language. The
   * names are those that {@link ComparePage} writes.
   *
   * @param from the first day of the earlier version
   * @param to the first day of the later version
   * @param act the CELEX number of the act that the comparison is limited to, or null
   * @param eId the eIds of the occurrences
   * @param sides true if the answer is the wording on each of the two days, side by side, or null
   * @param fromDay the earlier day of the page, if it is not {@code from}, or null
   * @param toDay the later day of the page, if it is not {@code to}, or null
   */
  record PlaceRequest(
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @Nullable String act,
      @NotEmpty List<String> eId,
      @Nullable Boolean sides,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate fromDay,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate toDay) {

    PlaceRequest {
      eId = eId == null ? List.of() : List.copyOf(eId);
    }
  }

  /**
   * What the address of the differences of an act or another act asks for, beside the act and the
   * language. The names are those that {@link ComparePage} writes.
   *
   * @param from the first day of the earlier version
   * @param to the first day of the later version
   * @param act the CELEX number of the act that the comparison is limited to
   */
  record EitherRequest(
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @NotNull String act) {}
}
