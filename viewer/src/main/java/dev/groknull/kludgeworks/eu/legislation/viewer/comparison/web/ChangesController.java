// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.StepCounts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Requests;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.RowFolds;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ActsView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ComparisonView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.NeighbourView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.RowFoldLinks;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteParts;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer;
import gg.jte.generated.precompiled.Templates;
import gg.jte.output.StringOutput;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
 * the {@code comparepage} module writes them:
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
 * <p>The comparison module compares the texts by their roots. Each difference has a sidenote. The
 * sidenote gets the acts that made the difference from the facts of the act.
 */
@Controller
class ChangesController {
  private static final Logger LOG = LoggerFactory.getLogger(ChangesController.class);

  /** The rows of a fold, as its address gives them. For example, {@code 10-40}. */
  private static final Pattern ROWS = Pattern.compile("(\\d{1,9})-(\\d{1,9})");

  private final Acts acts;
  private final Texts texts;
  private final Comparisons comparisons;
  private final StepCounts stepCounts;
  private final Content content;
  private final Templates templates;
  private final LegalRenderer html;

  ChangesController(
      Acts acts,
      Texts texts,
      Comparisons comparisons,
      StepCounts stepCounts,
      Content content,
      Templates templates,
      LegalRenderer html) {
    this.acts = acts;
    this.texts = texts;
    this.comparisons = comparisons;
    this.stepCounts = stepCounts;
    this.content = content;
    this.templates = templates;
    this.html = html;
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
      @RequestParam(name = ComparePage.ACT, required = false) @Nullable String act,
      @RequestParam(name = ComparePage.VIEW, defaultValue = "changes") String shown,
      HttpServletRequest request) {
    var view = view(shown);
    var ends = new Ends(new Eli(type, year, number), code, from, to, act);
    var page =
        ComparePage.ofPart(
            request.getRequestURI(),
            from,
            to,
            ends.only == null ? null : ends.only.celex(),
            view.parameter());
    var started = System.nanoTime();
    var preparation = texts.preparation(ends.act, ends.language);
    var only = ends.only;
    var step = only == null ? null : ends.step(only).orElseThrow(Requests::notFound);
    var earlier = step == null ? ends.from : ends.earlier(step);
    var later = step == null ? ends.to : step;
    var attributed =
        attributed(ends, preparation, earlier, later, only, new HashMap<>())
            .orElseThrow(Requests::notFound);
    var changes =
        only == null
            ? ChangesView.of(attributed.comparison())
            : ChangesView.of(
                attributed.comparison(),
                // Also show the differences that the act may have made. For these, it is not
                // known which act made them.
                difference ->
                    attributed.sidenote(difference).acts().contains(only)
                        || attributed.sidenote(difference).candidates().contains(only),
                "not changed by " + only.cited());
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
    var all =
        only == null
            ? null
            : new ControlsView.Only(only.cited(), page.href(null, view.parameter()));
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
                      "/content/" + attributed.later().hex() + "/units",
                      page,
                      views,
                      all)
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
                                each,
                                sidenote,
                                greyed(only, sidenote),
                                unit,
                                each.part(page),
                                parts);
                          }),
                      parts,
                      views,
                      all,
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
                    earlier.celex(),
                    later.celex(),
                    views,
                    all,
                    page.same(earlier.date(), later.date()),
                    page)
                .render();
          }
        };
    LOG.info(
        "{} of {} from {} to {} in {}{}: {} of {} differences, {} of Cellar's amendments"
            + " between them at their units, {} at none, in {} ms",
        view.label(),
        ends.act.id(),
        earlier.date(),
        later.date(),
        ends.language.code(),
        only == null ? "" : " by " + only,
        differences.size(),
        attributed.comparison().differences().size(),
        attributed.amended().placed(),
        attributed.amended().unplaced(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return html;
  }

  /**
   * Returns whether a difference with {@code sidenote} is greyed. It is greyed if the comparison is
   * limited to the changes of {@code only}, and that act may have made the difference, but it is
   * not known.
   */
  private static boolean greyed(@Nullable ActIdentifier only, @Nullable SidenoteView sidenote) {
    return only != null && sidenote != null && !sidenote.candidates().isEmpty();
  }

  /**
   * Returns the view that the address of a comparison asks for. Throws 404 for a view that does not
   * exist.
   */
  private static ComparisonView view(String code) {
    return ComparisonView.of(code).orElseThrow(Requests::notFound);
  }

  /**
   * Returns the acts between the versions of the comparison, which the compare page loads into its
   * acts slot.
   *
   * <p>Each act shows how many of the listed differences it made. The count uses the same
   * attribution as the sidenotes: {@link Act#amended} and the markers. Thus a chip counts the list
   * that it filters. If the two texts are not both built, no act gets a count. The counts are
   * computed once and kept in {@link StepCounts}.
   *
   * <p>If the counts are not kept yet, and {@code count} is not set, it returns the chips without
   * counts at once. They load their counts from this address with {@code count} set. Thus the chips
   * show while the counting runs.
   *
   * <p>Returns 404 if the address is not the canonical address of a comparison.
   */
  @GetMapping(path = ComparePage.ACTS_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String acts(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(ComparePage.FROM) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(ComparePage.TO) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(name = ComparePage.ACT, required = false) @Nullable String act,
      @RequestParam(name = ComparePage.VIEW, defaultValue = "changes") String shown,
      @RequestParam(name = ComparePage.COUNT, defaultValue = "false") boolean count,
      HttpServletRequest request) {
    var view = view(shown);
    var ends = new Ends(new Eli(type, year, number), code, from, to, act);
    var page =
        ComparePage.ofPart(
            request.getRequestURI(),
            from,
            to,
            ends.only == null ? null : ends.only.celex(),
            view.parameter());
    var started = System.nanoTime();
    var preparation = texts.preparation(ends.act, ends.language);
    var between = ends.act.integratedBetween(ends.from, ends.to);
    var counts = new HashMap<ActIdentifier, Integer>();
    var unknown = 0;
    // Count the list that the page shows. Do not add up the counts of single steps. That sum counts
    // a provision once for each step that changed it. It also counts a change that one act made
    // and the next act undid.
    var kept = false;
    if (preparation.texts().get(ends.from) instanceof Text.Ready before
        && preparation.texts().get(ends.to) instanceof Text.Ready after) {
      var found = stepCounts.find(ends.act.id(), before.root(), after.root());
      if (found.isEmpty() && !count) {
        return templates
            .comparisonActs(
                ActsView.of(
                    ends.act,
                    ends.from,
                    ends.to,
                    ends.language,
                    page,
                    Map.of(),
                    page.countedActs()))
            .render();
      }
      var counted = found.or(() -> count(ends, preparation, ends.from, ends.to, new HashMap<>()));
      kept = found.isPresent();
      if (counted.isPresent()) {
        if (!kept) {
          stepCounts.add(ends.act.id(), before.root(), after.root(), counted.get());
        }
        counted
            .get()
            .differences()
            .forEach(
                (each, differences) -> {
                  if (between.contains(each)) {
                    counts.put(each, differences);
                  }
                });
        unknown = counted.get().unknown();
      }
    }
    LOG.info(
        "Acts of {} from {} to {} in {}: {} of {} counted{}, in {} ms",
        ends.act.id(),
        ends.from.date(),
        ends.to.date(),
        ends.language.code(),
        counts.size(),
        between.size(),
        kept ? " (kept)" : "",
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return templates
        .comparisonActs(
            ActsView.of(ends.act, ends.from, ends.to, ends.language, page, counts, null)
                .counted(unknown))
        .render();
  }

  /**
   * Returns the counts of the step from {@code from} to {@code to}:
   *
   * <ul>
   *   <li>how many differences each act of the step made
   *   <li>how many differences more than one act made together
   *   <li>how many differences one of the acts made, where it is not known which act
   * </ul>
   *
   * <p>Returns none if either text is not built.
   */
  private Optional<StepCounts.Counted> count(
      Ends ends,
      Preparation preparation,
      Version from,
      Version to,
      Map<ContentHash, Outline> outlines) {
    return attributed(ends, preparation, from, to, null, outlines)
        .map(
            attributed -> {
              var differences = new HashMap<ActIdentifier, Integer>();
              ends.act.integratedBetween(from, to).forEach(each -> differences.put(each, 0));
              var together = 0;
              var unknown = 0;
              for (var sidenote : attributed.sidenotes().values()) {
                sidenote.acts().forEach(each -> differences.merge(each, 1, Integer::sum));
                together += sidenote.acts().size() > 1 ? 1 : 0;
                unknown += sidenote.candidates().isEmpty() ? 0 : 1;
              }
              return new StepCounts.Counted(differences, together, unknown);
            });
  }

  /**
   * Returns the comparison of {@code from} with {@code to}, with a sidenote for each difference.
   * The sidenotes are limited to the changes of {@code only}, if it is given. Each outline is read
   * once and kept in {@code outlines}, by root. Returns none if either text is not built.
   */
  private Optional<Attributed> attributed(
      Ends ends,
      Preparation preparation,
      Version from,
      Version to,
      @Nullable ActIdentifier only,
      Map<ContentHash, Outline> outlines) {
    if (!(preparation.texts().get(from) instanceof Text.Ready earlier)
        || !(preparation.texts().get(to) instanceof Text.Ready later)) {
      return Optional.empty();
    }
    var comparison = comparisons.between(earlier.root(), later.root());
    if (comparison.isEmpty()) {
      return Optional.empty();
    }
    var before = outline(earlier.root(), outlines);
    var outline = outline(later.root(), outlines);
    var amended = ends.act.amended(from, to, before.eIds(), outline.eIds(), ends.language);
    var sidenotes = new IdentityHashMap<Difference, SidenoteView>();
    for (var difference : comparison.get().differences()) {
      sidenotes.put(
          difference,
          SidenoteView.of(
              ends.act,
              from,
              to,
              difference,
              amended.of(difference.eId(), difference.kind() != Kind.CHANGED),
              only,
              ends.language));
    }
    return Optional.of(
        new Attributed(comparison.get(), later.root(), before, outline, amended, sidenotes));
  }

  private Outline outline(ContentHash root, Map<ContentHash, Outline> outlines) {
    return outlines.computeIfAbsent(root, each -> content.outline(each).orElseGet(Outline::empty));
  }

  /**
   * A comparison, each difference with its sidenote.
   *
   * @param comparison the comparison
   * @param later the later text's root
   * @param earlier the earlier text's outline
   * @param outline the later text's outline
   * @param amended Cellar's amendments between the two versions, at the units of their texts
   * @param sidenotes each difference's sidenote, by the difference
   */
  private record Attributed(
      Comparison comparison,
      ContentHash later,
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
   */
  @GetMapping(path = ComparePage.DIFFERENCE_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String difference(
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
      HttpServletRequest request) {
    var ends = new Ends(new Eli(type, year, number), code, from, to, act);
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
    var difference =
        comparisons.difference(before.root(), after.root(), eId).orElseThrow(Requests::notFound);
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
    var ends = new Ends(new Eli(type, year, number), code, from, to, null);
    var started = System.nanoTime();
    if (!(texts.preparation(ends.act, ends.language).texts().get(ends.to)
        instanceof Text.Ready later)) {
      throw Requests.notFound();
    }
    var text = content.read(later.root()).orElseThrow(Requests::notFound);
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

  /**
   * The two ends of a comparison, from its canonical address:
   *
   * <ul>
   *   <li>the act, and its versions in the language
   *   <li>the versions that start on {@code from} and on {@code to}. From is not after To.
   *   <li>the act asked for, to limit the comparison to its changes. It is between the two
   *       versions.
   * </ul>
   *
   * <p>Anything else is 404. The compare page finds the canonical address for any dates. Its parts
   * need not.
   */
  private final class Ends {
    final Language language;
    final Act act;
    final Timeline timeline;
    final Version from;
    final Version to;
    final @Nullable ActIdentifier only;

    Ends(Eli eli, String code, LocalDate from, LocalDate to, @Nullable String only) {
      language = Requests.language(code);
      act = acts.find(eli).orElseThrow(Requests::notFound);
      timeline = act.timeline().in(language).orElseThrow(Requests::notFound);
      this.from = timeline.startingOn(from).orElseThrow(Requests::notFound);
      this.to = timeline.startingOn(to).orElseThrow(Requests::notFound);
      if (this.from.date().isAfter(this.to.date())) {
        throw Requests.notFound();
      }
      this.only =
          only == null
              ? null
              : act.integratedBetween(this.from, this.to).stream()
                  .filter(each -> each.celex().equals(only))
                  .findFirst()
                  .orElseThrow(Requests::notFound);
    }

    /**
     * Returns the version in the language that integrates {@code amending}. The version is after
     * From and not after To.
     */
    Optional<Version> step(ActIdentifier amending) {
      return timeline.versions().stream()
          .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
          .filter(v -> act.integratedBetween(earlier(v), v).contains(amending))
          .findFirst();
    }

    /**
     * Returns the version in the language before {@code version}. That is the text just before its
     * changes. If there is no version before it, returns {@code version}.
     */
    Version earlier(Version version) {
      return timeline.predecessor(version).orElse(version);
    }
  }
}
