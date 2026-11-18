// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.RowFolds;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.DifferVersion;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ComparisonView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.NeighbourView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.RowFoldLinks;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteParts;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Release;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import gg.jte.output.StringOutput;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * Serves the parts of the compare page that come from the comparison of two versions of an act.
 *
 * <p>The compare page is the catalogue's, at {@link ComparePage#PATH}. It has slots that htmx fills
 * from the addresses here, each the page's address with a segment added and the page's query, as
 * the catalogue's {@link ComparePage} writes them:
 *
 * <ul>
 *   <li>{@code /changes}: the changes, in the view that {@code view} asks for
 *   <li>{@code /acts}: the acts between the two versions, as chips with how many differences each
 *       made
 *   <li>{@code /same}: the units of a fold of the Synopsis, which the Synopsis loads itself
 *   <li>{@code /difference}: one difference by the eId of its unit, or some rows of one of its
 *       tables. The views load it when the reader opens an annex inserted whole or a fold of rows.
 * </ul>
 *
 * <p>The parts answer only the canonical address of a comparison, as the page links to it: the
 * first days of two versions in the language, the earlier first, and an act between them, if any.
 * Anything else is 404: the page redirects to the canonical address before it loads its parts.
 *
 * <p>The comparison module compares the texts by their identities. Each difference has a sidenote.
 * The sidenote gets the acts that made the difference from the facts of the act.
 */
@Controller
class ChangesController {
  private static final Logger LOG = LoggerFactory.getLogger(ChangesController.class);

  /** The rows of a fold, as its address gives them. For example, {@code 10-40}. */
  private static final Pattern ROWS = Pattern.compile("(\\d{1,9})-(\\d{1,9})");

  private final Acts acts;
  private final Texts texts;
  private final Comparisons comparisons;
  private final Content content;

  /**
   * The list of the changes that one of two acts made shows the wording of each change when it
   * opens, up to this many changes. The page of an amending act has the same limit for its own
   * changes.
   */
  private static final int OPEN = 25;

  private final Templates templates;
  private final LegalRenderer html;
  private final Release release;
  private final Clock clock;

  ChangesController(
      Acts acts,
      Texts texts,
      Comparisons comparisons,
      Content content,
      Templates templates,
      LegalRenderer html,
      Release release,
      Clock clock) {
    this.acts = acts;
    this.texts = texts;
    this.comparisons = comparisons;
    this.content = content;
    this.templates = templates;
    this.html = html;
    this.release = release;
    this.clock = clock;
  }

  /**
   * Returns the changes of the comparison, in the view that {@code view} asks for. The compare page
   * loads them when both texts are built.
   *
   * <p>The comparison module supplies the comparison. If no one asked for it before, the module
   * computes it now. The outline of the later text shows beside the changes. Each difference has a
   * sidenote. The sidenote comes from the amendment markers and from the amendments that Cellar
   * records for the unit, {@link Act#amended}.
   *
   * <p>If the comparison is limited to the changes of one act, it compares the version that
   * integrates that act with the version before it. That is the wording just before the change
   * against the wording just after it. The view shows the differences that the act made and folds
   * the others.
   *
   * <p>{@code fromDay} and {@code toDay} are the two days of the page, where they are not the first
   * days of the two versions. The links of the views keep them. A difference with a change that
   * does not apply on the later day yet has an amber rule, and its sidenote says so ({@link
   * Act#pending}).
   *
   * <p>Returns 404 if the address is not the canonical address of a comparison, if the view does
   * not exist, or if the texts are not built yet.
   */
  @GetMapping(path = ComparePage.CHANGES_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String changes(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(ComparePage.FROM) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(ComparePage.TO) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(name = ComparePage.VIEW, defaultValue = "changes") String shown,
      @RequestParam(name = ComparePage.FROM_DAY, required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate fromDay,
      @RequestParam(name = ComparePage.TO_DAY, required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate toDay,
      HttpServletRequest request) {
    var view = view(shown);
    var ends = ends(new Eli(type, year, number), code, from, to, null);
    var page =
        ComparePage.ofPart(request.getRequestURI(), from, to, null, view.parameter())
            .on(fromDay, toDay);
    var started = System.nanoTime();
    var preparation = texts.preparation(ends.act, ends.language);
    var earlier = ends.from;
    var later = ends.to;
    var attributed =
        attributed(ends, preparation, earlier, later, null, page.later(), new HashMap<>())
            .orElseThrow(Requests::notFound);
    var changes = ChangesView.of(attributed.comparison());
    var sidenotes = new HashMap<Integer, SidenoteView>();
    var differences = new ArrayList<ChangesView.Shown>();
    for (var passage : changes.passages()) {
      if (passage instanceof ChangesView.Shown each) {
        sidenotes.put(each.number(), attributed.sidenote(each.difference()));
        differences.add(each);
      }
    }
    var parts = SidenoteParts.of(sidenotes.values());
    var outline =
        OutlineView.of(
            attributed.outline(), differences.stream().map(ChangesView.Shown::difference).toList());
    var views = ControlsView.views(page, view);
    var html =
        switch (view) {
          case CHANGES ->
              templates
                  .comparisonChanges(
                      changes,
                      outline,
                      sidenotes,
                      parts,
                      this.html,
                      "/content/" + attributed.later() + "/units",
                      page,
                      views)
                  .render();
          case WHOLE ->
              templates
                  .comparisonWhole(
                      changes,
                      outline,
                      WholeText.of(
                          content.read(attributed.later()).orElseThrow(Requests::notFound),
                          attributed.earlier().eIds(),
                          differences,
                          this.html,
                          each -> RowFoldLinks.of(each.difference().shown(), each.part(page)),
                          (each, unit) -> {
                            var sidenote = sidenotes.get(each.number());
                            return templates.comparisonDifference(
                                each, sidenote, unit, page, parts);
                          }),
                      parts,
                      views,
                      ControlsView.legend(changes, sidenotes))
                  .render();
          case SYNOPSIS -> {
            // Show the neighbours of the differences. Each neighbour shows what last changed it.
            var same = new HashSet<String>();
            for (var passage : changes.passages()) {
              if (passage instanceof ChangesView.Fold fold && fold.same()) {
                fold.beside().forEach(unit -> same.add(unit.eId()));
              }
            }
            var units = new HashMap<String, Element>();
            var histories = new HashMap<String, SidenoteView.History>();
            if (!same.isEmpty()) {
              var text = content.read(attributed.later()).orElseThrow(Requests::notFound);
              Neighbours.of(text, same)
                  .forEach(
                      (eId, neighbour) -> {
                        units.put(eId, neighbour.unit());
                        histories.put(
                            eId,
                            SidenoteView.history(
                                ends.act, later, neighbour.markers(), ends.language));
                      });
            }
            yield templates
                .comparisonSynopsis(
                    changes,
                    outline,
                    sidenotes,
                    parts,
                    units,
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
            + " between them at their units, {} at none, in {} ms",
        view.label(),
        ends.act.id(),
        earlier.date(),
        later.date(),
        ends.language.code(),
        differences.size(),
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
   * Returns the comparison of {@code from} with {@code to}, with a sidenote for each difference.
   * The sidenotes are limited to the changes of {@code only}, if it is given. With {@code day}, the
   * later day of the comparison, a sidenote says where a change does not apply on that day yet.
   * Each outline is read once and kept in {@code outlines}, by text. Returns none if either text is
   * not built.
   */
  private Optional<Attributed> attributed(
      Ends ends,
      Preparation preparation,
      Version from,
      Version to,
      @Nullable ActIdentifier only,
      @Nullable LocalDate day,
      Map<TextId, Outline> outlines) {
    if (!(preparation.texts().get(from) instanceof Text.Ready earlier)
        || !(preparation.texts().get(to) instanceof Text.Ready later)) {
      return Optional.empty();
    }
    var comparison = comparisons.between(earlier.text(), later.text());
    if (comparison.isEmpty()) {
      return Optional.empty();
    }
    var before = outline(earlier.text(), outlines);
    var outline = outline(later.text(), outlines);
    var amended = ends.act.amended(from, to, before.eIds(), outline.eIds(), ends.language);
    var pending =
        day == null ? null : ends.act.pending(to, day, ends.language, texts.marks(preparation));
    var today = Timeline.today(clock);
    var sidenotes = new IdentityHashMap<Difference, SidenoteView>();
    for (var difference : comparison.get().differences()) {
      var sidenote =
          SidenoteView.of(
              ends.act,
              from,
              to,
              difference,
              amended.of(difference.eId(), difference.kind() != Kind.CHANGED),
              only,
              ends.language);
      sidenotes.put(
          difference,
          pending == null
              ? sidenote
              : pending
                  .at(difference.eId())
                  .map(each -> sidenote.notApplying(ends.act, each, pending.day(), today))
                  .orElse(sidenote));
    }
    return Optional.of(
        new Attributed(comparison.get(), later.text(), before, outline, amended, sidenotes));
  }

  private Outline outline(TextId text, Map<TextId, Outline> outlines) {
    return outlines.computeIfAbsent(text, each -> content.outline(each).orElseGet(Outline::empty));
  }

  /**
   * A comparison, each difference with its sidenote.
   *
   * @param comparison the comparison
   * @param later the later text
   * @param earlier the earlier text's outline
   * @param outline the later text's outline
   * @param amended Cellar's amendments between the two versions, at the units of their texts
   * @param sidenotes each difference's sidenote, by the difference
   */
  private record Attributed(
      Comparison comparison,
      TextId later,
      Outline earlier,
      Outline outline,
      Amended amended,
      Map<Difference, SidenoteView> sidenotes) {

    SidenoteView sidenote(Difference difference) {
      return Objects.requireNonNull(sidenotes.get(difference));
    }
  }

  /**
   * Returns one difference of the comparison, the one at the unit {@code eId}, as the views mark
   * it. With {@code table} and {@code rows}, it returns only those rows of that table of the unit,
   * as they read ({@link RowFolds}). The views load it when the reader opens an annex inserted
   * whole or a fold of rows, so that a long comparison does not send them until they are asked for.
   *
   * <p>If the comparison is limited to the changes of one act, the difference is that of the act's
   * own comparison, as for the changes.
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
    var ends = ends(new Eli(type, year, number), code, from, to, act);
    var page =
        ComparePage.ofPart(
            request.getRequestURI(), from, to, ends.only == null ? null : ends.only.celex(), null);
    var started = System.nanoTime();
    var preparation = texts.preparation(ends.act, ends.language);
    var only = ends.only;
    var step = only == null ? null : ends.step(only).orElseThrow(Requests::notFound);
    var earlier = step == null ? ends.from : ends.earlier(step);
    var later = step == null ? ends.to : step;
    if (!(preparation.texts().get(earlier) instanceof Text.Ready before)
        || !(preparation.texts().get(later) instanceof Text.Ready after)) {
      throw Requests.notFound();
    }
    if (Release.notModified(
        request,
        response,
        Release.REVALIDATED,
        release.etag(before.text(), after.text(), DifferVersion.value()))) {
      return null;
    }
    if (outline) {
      var entry =
          content
              .outline(after.text())
              .flatMap(each -> entry(each.entries(), eId))
              .orElseThrow(Requests::notFound);
      return templates.comparisonAnnexoutline(entry.entries(), page).render();
    }
    var difference =
        comparisons.difference(before.text(), after.text(), eId).orElseThrow(Requests::notFound);
    if (table == null && rows == null) {
      var html = render(new ChangesView.Shown(0, difference, "").folded(page));
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
    found.forEach(row -> html.append(render(row)));
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
   * Returns the differences at the units {@code eIds} of the comparison, as the views mark them
   * ({@link Comparison#at}). The page of an amending act loads the wording of one of its changes
   * from here. If the units are the same in both texts, it says so.
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
    var ends = ends(new Eli(type, year, number), code, from, to, act);
    var page =
        ComparePage.ofPart(
                request.getRequestURI(),
                from,
                to,
                ends.only == null ? null : ends.only.celex(),
                null)
            .on(fromDay, toDay);
    var started = System.nanoTime();
    var preparation = texts.preparation(ends.act, ends.language);
    var only = ends.only;
    var step = only == null ? null : ends.step(only).orElseThrow(Requests::notFound);
    var earlier = step == null ? ends.from : ends.earlier(step);
    var later = step == null ? ends.to : step;
    if (!(preparation.texts().get(earlier) instanceof Text.Ready before)
        || !(preparation.texts().get(later) instanceof Text.Ready after)) {
      throw Requests.notFound();
    }
    if (Release.notModified(
        request,
        response,
        Release.REVALIDATED,
        release.etag(before.text(), after.text(), DifferVersion.value()))) {
      return null;
    }
    var found =
        comparisons.between(before.text(), after.text()).orElseThrow(Requests::notFound).at(eIds);
    String lead = null;
    if (found.isEmpty() && only != null) {
      // A chapter that the annex keeps flat has no difference of its own. Show the differences in
      // its points and its attachments that the text marks with the act.
      var attributed =
          attributed(ends, preparation, earlier, later, only, null, new HashMap<>())
              .orElseThrow(Requests::notFound);
      found =
          attributed.comparison().differences().stream()
              .filter(each -> eIds.stream().anyMatch(eId -> Location.holds(eId, each.eId())))
              .filter(each -> attributed.sidenote(each).acts().contains(only))
              .toList();
      lead =
          found.isEmpty()
              ? null
              : "The text marks these changes in this part of the annex with " + only.cited() + ".";
    }
    var shown = found.stream().map(difference -> new ChangesView.Shown(0, difference, "")).toList();
    var rendered =
        sides
            ? templates
                .comparisonSides(
                    shown, html, page, Dates.inWords(page.earlier()), Dates.inWords(page.later()))
                .render()
            : templates.comparisonPlace(shown, html, page, lead).render();
    LOG.info(
        "Place of {} from {} to {} in {}{} at {} units: {} differences, in {} ms",
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
   * data does not say which. These are the differences with no mark of an act, in a version that
   * integrates two acts or more. The page of an amending act shows them in a separate list, under
   * "By this act or by …". If there are none, the part is empty.
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
    var ends = ends(new Eli(type, year, number), code, from, to, act);
    var only = Objects.requireNonNull(ends.only);
    var page = ComparePage.ofPart(request.getRequestURI(), from, to, only.celex(), null);
    var started = System.nanoTime();
    var step = ends.step(only).orElseThrow(Requests::notFound);
    var attributed =
        attributed(
                ends,
                texts.preparation(ends.act, ends.language),
                ends.earlier(step),
                step,
                only,
                null,
                new HashMap<>())
            .orElseThrow(Requests::notFound);
    var others = new LinkedHashSet<ActIdentifier>();
    var shown = new ArrayList<ChangesView.Shown>();
    for (var difference : attributed.comparison().differences()) {
      var candidates = attributed.sidenote(difference).candidates();
      if (candidates.contains(only)) {
        candidates.stream().filter(each -> !each.equals(only)).forEach(others::add);
        shown.add(new ChangesView.Shown(0, difference, ""));
      }
    }
    var rendered =
        templates
            .comparisonEither(
                ChangesView.either(others.stream().map(ends.act::cited).toList()),
                shown,
                shown.size() <= OPEN,
                html,
                page)
            .render();
    LOG.info(
        "Differences of {} from {} to {} in {} by {} or another act: {}, in {} ms",
        ends.act.id(),
        ends.earlier(step).date(),
        step.date(),
        ends.language.code(),
        only,
        shown.size(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return rendered;
  }

  /** Returns the entry of the unit {@code eId} in {@code entries}, at any depth. */
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

  /** Returns {@code element} rendered as the legal text renders it. */
  private String render(Element element) {
    var out = new StringOutput();
    html.element(element).writeTo(out);
    return out.toString();
  }

  /**
   * Returns the units of a Synopsis fold, which opens in place.
   *
   * <p>Each unit is a row. The row shows the unit of the later text in both columns, because the
   * unit is the same in both texts. The row also shows what last changed the unit.
   *
   * <p>Returns 404 if the address is not the canonical address of a comparison, or if the later
   * text is not built yet.
   */
  @GetMapping(path = ComparePage.SAME_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String same(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(ComparePage.FROM) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(ComparePage.TO) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(ComparePage.FIRST) String first,
      @RequestParam(ComparePage.LAST) String last) {
    var ends = ends(new Eli(type, year, number), code, from, to, null);
    var started = System.nanoTime();
    if (!(texts.preparation(ends.act, ends.language).texts().get(ends.to)
        instanceof Text.Ready later)) {
      throw Requests.notFound();
    }
    var text = content.read(later.text()).orElseThrow(Requests::notFound);
    var eIds = new ArrayList<String>();
    for (var unit : text.units(first, last)) {
      eIds.add(Objects.requireNonNull(unit.attribute("eId")));
    }
    var found = Neighbours.of(text, Set.copyOf(eIds));
    var rows = new ArrayList<NeighbourView>();
    for (var eId : eIds) {
      var neighbour = found.get(eId);
      if (neighbour != null) {
        rows.add(
            new NeighbourView(
                Neighbours.sibling(neighbour.unit()),
                neighbour.unit(),
                SidenoteView.history(ends.act, ends.to, neighbour.markers(), ends.language)));
      }
    }
    LOG.info(
        "Same units of {} from {} to {} in {}: {} of {}, in {} ms",
        ends.act.id(),
        ends.from.date(),
        ends.to.date(),
        ends.language.code(),
        rows.size(),
        eIds.size(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return templates.comparisonNeighbours(rows, this.html).render();
  }

  /** Returns the ends of the comparison at a canonical address of the compare page. */
  private Ends ends(Eli eli, String code, LocalDate from, LocalDate to, @Nullable String only) {
    var language = Requests.language(code);
    return new Ends(acts.find(eli).orElseThrow(Requests::notFound), language, from, to, only);
  }
}
