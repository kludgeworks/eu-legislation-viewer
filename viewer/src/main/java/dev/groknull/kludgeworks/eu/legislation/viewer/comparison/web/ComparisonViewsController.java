// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActAddress;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Today;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributions;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.LastChange;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Neighbours;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ComparisonView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.NeighbourView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.RowFoldLinks;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenotePieces;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Release;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
 * Serves the comparison of two versions of an act, in one of its three views, for the compare page:
 * Differences, Whole text and Synopsis.
 *
 * <p>The compare page is the catalogue's, at {@link ComparePage#PATH}. It has slots that htmx fills
 * from the addresses here, each the page's address with a segment added and the page's query, as
 * the catalogue's {@link ComparePage} writes them:
 *
 * <ul>
 *   <li>{@code /differences}: the differences, in the view that {@code view} asks for
 *   <li>{@code /same}: the occurrences of a fold of the Synopsis, which the Synopsis loads itself
 * </ul>
 *
 * <p>The fragments answer only the canonical address of a comparison, as the page links to it: the
 * first days of two versions in the language, the earlier first. Anything else is 404: the page
 * redirects to the canonical address before it loads its fragments.
 *
 * <p>The comparison module compares the texts by their identities. Each difference has a sidenote.
 * {@link Attributions} finds who made each difference. The sidenote puts it into words. {@link
 * DifferenceController} serves one difference.
 */
/*
 * A handler takes the act and the language of its address as an ActAddress, which the catalogue
 * gives. The check knows only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
@Controller
class ComparisonViewsController {
  private static final Logger LOG = LoggerFactory.getLogger(ComparisonViewsController.class);

  private final Attributions attributions;
  private final Content content;
  private final Templates templates;
  private final LegalRenderer html;
  private final Today today;
  private final Fragments fragments;

  ComparisonViewsController(
      Acts acts,
      Texts texts,
      Attributions attributions,
      Content content,
      Templates templates,
      LegalRenderer html,
      Release release,
      Today today) {
    this.attributions = attributions;
    this.content = content;
    this.templates = templates;
    this.html = html;
    this.today = today;
    this.fragments = new Fragments(acts, texts, release, html);
  }

  /**
   * Returns the differences of the comparison, in the view that {@code view} asks for. The compare
   * page loads them when both texts are built.
   *
   * <p>The comparison module supplies the comparison. If no one asked for it before, the module
   * computes it now. The outline of the later text shows beside the differences. Each difference
   * has a sidenote. {@link Attributions} finds who made each difference, from the tags and from the
   * amendments that Cellar records for the occurrence. The sidenote puts it into words.
   *
   * <p>If the comparison is limited to the changes of one act, it compares the version that
   * integrates that act with the version before it. That is the wording just before the change
   * against the wording just after it. The view shows the differences that the act made and folds
   * the others.
   *
   * <p>{@code fromDay} and {@code toDay} are the two days of the page, where they are not the first
   * days of the two versions. The links of the views keep them. A difference with a change that
   * does not apply on the later day yet, a prospective amendment, has an amber bar, and its
   * sidenote says so. {@link Attributions} finds these changes too.
   *
   * <p>Returns 404 if the address is not the canonical address of a comparison, if the view does
   * not exist, or if the texts are not built yet.
   */
  @GetMapping(path = ComparePage.DIFFERENCES_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String differences(
      ActAddress address,
      @Valid @ModelAttribute DifferencesRequest asked,
      HttpServletRequest request) {
    var from = asked.from();
    var to = asked.to();
    var fromDay = asked.fromDay();
    var toDay = asked.toDay();
    var view = view(Objects.requireNonNullElse(asked.view(), ComparePage.DIFFERENCES));
    var ends = fragments.ends(address, from, to, null);
    var page =
        ComparePage.ofFragment(request.getRequestURI(), from, to, null, view.parameter())
            .on(fromDay, toDay);
    var started = System.nanoTime();
    var preparation = ends.preparation;
    var earlier = ends.from;
    var later = ends.to;
    var attributed =
        attributions
            .of(ends.act, ends.versions(), earlier, later, page.later())
            .orElseThrow(Requests::notFound);
    var today = this.today.today();
    var differences = DifferencesView.of(attributed.comparison());
    var sidenotes = new HashMap<Integer, SidenoteView>();
    var shown = new ArrayList<DifferencesView.Shown>();
    for (var passage : differences.passages()) {
      if (passage instanceof DifferencesView.Shown each) {
        sidenotes.put(
            each.number(),
            SidenoteView.of(
                ends.act, attributed.of(each.difference()), null, today, ends.language));
        shown.add(each);
      }
    }
    var pieces = SidenotePieces.of(sidenotes.values());
    var outline =
        OutlineView.of(
            attributed.outline(), shown.stream().map(DifferencesView.Shown::difference).toList());
    var views = ControlsView.views(page, view);
    var html =
        switch (view) {
          case DIFFERENCES ->
              templates
                  .comparisonDifferences(
                      differences,
                      outline,
                      sidenotes,
                      pieces,
                      this.html,
                      "/content/" + attributed.later() + "/occurrences",
                      page,
                      views)
                  .render();
          case WHOLE ->
              templates
                  .comparisonWhole(
                      differences,
                      outline,
                      WholeText.of(
                          content.read(attributed.later()).orElseThrow(Requests::notFound),
                          shown,
                          this.html,
                          each -> RowFoldLinks.of(each.difference().shown(), each.fragment(page)),
                          (each, occurrence) -> {
                            var sidenote = sidenotes.get(each.number());
                            return templates.comparisonDifference(
                                each, sidenote, occurrence, page, pieces);
                          }),
                      pieces,
                      views,
                      ControlsView.legend(differences, sidenotes))
                  .render();
          case SYNOPSIS -> {
            // Show the neighbours of the differences. Each neighbour shows what last changed it.
            var same = new HashSet<String>();
            for (var passage : differences.passages()) {
              if (passage instanceof DifferencesView.Fold fold) {
                fold.beside().forEach(occurrence -> same.add(occurrence.eId()));
              }
            }
            var occurrences = new HashMap<String, Element>();
            var histories = new HashMap<String, SidenoteView.History>();
            if (!same.isEmpty()) {
              var text = content.read(attributed.later()).orElseThrow(Requests::notFound);
              Neighbours.of(text, same)
                  .forEach((eId, neighbour) -> {
                    occurrences.put(eId, neighbour.occurrence());
                    histories.put(
                        eId,
                        SidenoteView.history(
                            ends.act,
                            LastChange.of(ends.act, ends.included, later, neighbour.tags()),
                            ends.language));
                  });
            }
            yield templates
                .comparisonSynopsis(
                    differences,
                    outline,
                    sidenotes,
                    pieces,
                    occurrences,
                    histories,
                    this.html,
                    Dates.inWords(page.earlier()),
                    Dates.inWords(page.later()),
                    views,
                    page.same(earlier.date(), later.date()),
                    page)
                .render();
          }
        };
    LOG.info(
        "{} of {} from {} to {} in {}: {} of {} differences, {} of Cellar's amendments"
            + " between them at their occurrences, {} at none, in {} ms",
        view.label(),
        ends.act.id(),
        earlier.date(),
        later.date(),
        ends.language.code(),
        shown.size(),
        attributed.comparison().differences().size(),
        attributed.amended().placed(),
        attributed.amended().unplaced(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return html;
  }

  /**
   * Returns the view that the address of a comparison asks for. Throws 404 for a view that does not
   * exist.
   */
  private static ComparisonView view(String code) {
    return ComparisonView.of(code).orElseThrow(Requests::notFound);
  }

  /**
   * Returns the occurrences of a Synopsis fold, which opens in place.
   *
   * <p>Each occurrence is a row. The row shows the occurrence of the later text in both columns,
   * because the occurrence is the same in both texts. The row also shows what last changed the
   * occurrence.
   *
   * <p>Returns 404 if the address is not the canonical address of a comparison, or if the later
   * text is not built yet.
   */
  @GetMapping(path = ComparePage.SAME_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String same(ActAddress address, @Valid @ModelAttribute SameRequest asked) {
    var first = asked.first();
    var last = asked.last();
    var ends = fragments.ends(address, asked.from(), asked.to(), null);
    var started = System.nanoTime();
    if (!(ends.preparation.texts().get(ends.to) instanceof Text.Ready later)) {
      throw Requests.notFound();
    }
    var text = content.read(later.text()).orElseThrow(Requests::notFound);
    var eIds = new ArrayList<String>();
    for (var occurrence : text.occurrences(first, last)) {
      eIds.add(Objects.requireNonNull(occurrence.attribute("eId")));
    }
    var found = Neighbours.of(text, Set.copyOf(eIds));
    var rows = new ArrayList<NeighbourView>();
    for (var eId : eIds) {
      var neighbour = found.get(eId);
      if (neighbour != null) {
        rows.add(
            new NeighbourView(
                Neighbours.sibling(neighbour.occurrence()),
                neighbour.occurrence(),
                SidenoteView.history(
                    ends.act,
                    LastChange.of(ends.act, ends.included, ends.to, neighbour.tags()),
                    ends.language)));
      }
    }
    LOG.info(
        "Same occurrences of {} from {} to {} in {}: {} of {}, in {} ms",
        ends.act.id(),
        ends.from.date(),
        ends.to.date(),
        ends.language.code(),
        rows.size(),
        eIds.size(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return templates.comparisonNeighbours(rows, this.html).render();
  }

  /**
   * What the address of the differences asks for, beside the act and the language. The names are
   * those that {@link ComparePage} writes.
   *
   * @param from the first day of the earlier version
   * @param to the first day of the later version
   * @param view the code of the view. Without it, the view is Differences.
   * @param fromDay the earlier day of the page, if it is not {@code from}, or null
   * @param toDay the later day of the page, if it is not {@code to}, or null
   */
  record DifferencesRequest(
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @Nullable String view,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate fromDay,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate toDay) {}

  /**
   * What the address of a fold of the Synopsis asks for, beside the act and the language. The names
   * are those that {@link ComparePage} writes.
   *
   * @param from the first day of the earlier version
   * @param to the first day of the later version
   * @param first the eId of the first occurrence of the fold
   * @param last the eId of the last occurrence of the fold
   */
  record SameRequest(
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @NotNull String first,
      @NotNull String last) {}
}
