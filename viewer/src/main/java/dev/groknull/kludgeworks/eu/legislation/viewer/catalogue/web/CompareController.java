// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

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
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.NeighbourView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Wording;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Shows the comparison of two versions of an act, in a language.
 *
 * <p>For example, {@code /eli/reg_impl/2015/1998/eng/compare?from=2026-03-22&amp;to=2026-10-01}.
 * Each end can be any date. The comparison uses the version that applies on that date.
 *
 * <p>The page redirects to the canonical address of the comparison in these cases:
 *
 * <ul>
 *   <li>A date is not the first day of its version.
 *   <li>From is after To.
 *   <li>A date is missing. A missing To is the version that applies today. A missing From is the
 *       version before To. If the comparison is limited to the changes of one act and both dates
 *       are missing, To is the first version that integrates that act.
 *   <li>The act in {@code act} is not between the two versions.
 * </ul>
 *
 * <p>The act is prepared whole in the language, with every version, before the page shows changes.
 * Until then, the page is the preparing page, which becomes this page in place. Then the page loads
 * the changes of the two texts from {@code /changes}. The comparison module compares the texts by
 * their roots. Each difference has a sidenote. The sidenote gets the acts that made the difference
 * from the facts of the act.
 */
@Controller
class CompareController {
  private static final Logger LOG = LoggerFactory.getLogger(CompareController.class);

  private static final String PATH = "/eli/{type}/{year}/{number}/{language}/compare";

  private final Acts acts;
  private final Texts texts;
  private final Comparisons comparisons;
  private final StepCounts stepCounts;
  private final Content content;
  private final Templates templates;
  private final LegalRenderer html;
  private final Clock clock;

  CompareController(
      Acts acts,
      Texts texts,
      Comparisons comparisons,
      StepCounts stepCounts,
      Content content,
      Templates templates,
      LegalRenderer html,
      Clock clock) {
    this.acts = acts;
    this.texts = texts;
    this.comparisons = comparisons;
    this.stepCounts = stepCounts;
    this.content = content;
    this.templates = templates;
    this.html = html;
    this.clock = clock;
  }

  /**
   * Returns the comparison page, or redirects to the canonical address of the comparison.
   *
   * <p>While the act is being prepared in the language, it returns the preparing page. When the act
   * is prepared, that page requests this address again with {@code prepared}. The page then shows a
   * receipt that says what was prepared.
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
      @RequestParam(required = false) @Nullable String act,
      @RequestParam(name = "view", defaultValue = "changes") String shown,
      @RequestParam(defaultValue = "false") boolean prepared,
      @RequestParam(required = false) @Nullable String opened,
      @RequestParam(required = false) @Nullable String outside,
      HttpServletRequest request) {
    var view = view(shown);
    var page = new Page(new Eli(type, year, number), code, from, to, act, view);
    if (!page.at(from, to, act)) {
      var location = Links.compare(page.eli, page.from, page.to, page.language, page.only, view);
      // Tell the page what it chose, so that it can say so.
      if (from == null && to == null && act == null) {
        location += "&opened=current";
      } else if (act != null && page.only == null && ActIdentifier.isCelex(act)) {
        location += "&outside=" + act;
      }
      return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(location)).build();
    }
    var note =
        "current".equals(opened)
            ? "Showing the most recent amendment in force: the current version against the one"
                + " before."
            : outside != null && ActIdentifier.isCelex(outside)
                ? Wording.act(new ActIdentifier(outside))
                    + " isn’t between these two versions; showing every act’s changes."
                : null;
    var preparation = texts.prepare(page.act, page.language);
    var title = page.from.celex() + " → " + page.to.celex();
    var chrome = Chrome.of(request);
    var masthead = MastheadView.comparing(page.act, page.from, page.to, page.language);
    if (preparation.preparing()) {
      return ResponseEntity.ok()
          .contentType(Requests.HTML)
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
        .contentType(Requests.HTML)
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
                    CompareView.of(
                            page.act,
                            page.from,
                            page.to,
                            page.language,
                            today,
                            page.only,
                            view,
                            Links.acts(
                                page.eli, page.from, page.to, page.language, page.only, view))
                        .noting(note),
                    page.from.equals(page.to)
                        ? page.same()
                        : page.changes(preparation, self(request), chrome.results()),
                    prepared ? PreparingView.receipt(preparation, page.language) : null,
                    ArrivingView.of(preparation).orElse(null))
                .render());
  }

  /**
   * Returns the changes of the comparison, in the view that {@code view} asks for. The comparison
   * page loads them when both texts are built.
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
   * <p>Returns 404 if the address is not the canonical address of the comparison, or if the texts
   * are not built yet.
   */
  @GetMapping(path = PATH + "/changes", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String changes(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) @Nullable String act,
      @RequestParam(name = "view", defaultValue = "changes") String shown) {
    var view = view(shown);
    var page = new Page(new Eli(type, year, number), code, from, to, act, view);
    if (!page.at(from, to, act)) {
      throw Requests.notFound();
    }
    var started = System.nanoTime();
    var preparation = texts.preparation(page.act, page.language);
    var only = page.only;
    var step = only == null ? null : page.step(only).orElseThrow(Requests::notFound);
    var earlier = step == null ? page.from : page.earlier(step);
    var later = step == null ? page.to : step;
    var attributed =
        attributed(page, preparation, earlier, later, only, new HashMap<>())
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
                "not changed by " + Wording.act(only));
    var sidenotes = new HashMap<Integer, SidenoteView>();
    var differences = new ArrayList<ChangesView.Shown>();
    for (var passage : changes.passages()) {
      if (passage instanceof ChangesView.Shown each) {
        sidenotes.put(each.number(), attributed.sidenote(each.difference()));
        differences.add(each);
      }
    }
    var outline =
        OutlineView.of(
            attributed.outline(), differences.stream().map(ChangesView.Shown::difference).toList());
    var views = CompareView.views(page.eli, page.from, page.to, page.language, only, view);
    var withoutPreamble = attributed.comparison().withoutPreamble();
    var preamble =
        withoutPreamble == null
            ? ""
            : "not in the consolidation of "
                + Wording.date((withoutPreamble == Side.EARLIER ? earlier : later).date())
                + ", which the Publications Office published without it; no act changed it, so"
                + " it isn’t compared";
    var all =
        only == null
            ? null
            : new CompareView.Only(
                Wording.act(only),
                Links.compare(page.eli, page.from, page.to, page.language, null, view));
    var html =
        switch (view) {
          case CHANGES ->
              templates
                  .catalogueChanges(
                      changes,
                      outline,
                      sidenotes,
                      this.html,
                      "/content/" + attributed.later().hex() + "/units",
                      views,
                      all,
                      preamble)
                  .render();
          case WHOLE ->
              templates
                  .catalogueWhole(
                      changes,
                      outline,
                      WholeText.of(
                          content.read(attributed.later()).orElseThrow(Requests::notFound),
                          attributed.earlier().eIds(),
                          differences,
                          this.html,
                          (each, unit) -> {
                            var sidenote = sidenotes.get(each.number());
                            return templates.catalogueDifference(
                                each, sidenote, greyed(only, sidenote), unit);
                          }),
                      views,
                      all,
                      sidenotes.values().stream().anyMatch(SidenoteView::corrected))
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
                                page.act, later, neighbour.markers(), page.language));
                      });
            }
            yield templates
                .catalogueSynopsis(
                    changes,
                    outline,
                    sidenotes,
                    units,
                    histories,
                    this.html,
                    earlier.celex(),
                    later.celex(),
                    views,
                    all,
                    preamble,
                    Links.same(page.eli, earlier, later, page.language))
                .render();
          }
        };
    LOG.info(
        "{} of {} from {} to {} in {}{}: {} of {} differences, {} of Cellar's amendments"
            + " between them at their units, {} at none, in {} ms",
        view.label(),
        page.act.id(),
        earlier.date(),
        later.date(),
        page.language.code(),
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
  private static CompareView.View view(String code) {
    return CompareView.View.of(code).orElseThrow(Requests::notFound);
  }

  /**
   * Returns the acts between the versions of the comparison, which the comparison page loads.
   *
   * <p>Each act shows how many of the listed differences it made. The count uses the same
   * attribution as the sidenotes: {@link Act#amended} and the markers. Thus a chip counts the list
   * that it filters. If the two texts are not both built, no act gets a count. The counts are
   * computed once and kept in {@link StepCounts}.
   *
   * <p>Returns 404 if the address is not the canonical address of the comparison.
   */
  @GetMapping(path = PATH + "/acts", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String acts(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) @Nullable String act,
      @RequestParam(name = "view", defaultValue = "changes") String shown) {
    var view = view(shown);
    var page = new Page(new Eli(type, year, number), code, from, to, act, view);
    if (!page.at(from, to, act)) {
      throw Requests.notFound();
    }
    var started = System.nanoTime();
    var preparation = texts.preparation(page.act, page.language);
    var between = CompareView.between(page.act, page.from, page.to);
    var counts = new HashMap<ActIdentifier, Integer>();
    var together = 0;
    var unknown = 0;
    // Count the list that the page shows. Do not add up the counts of single steps. That sum counts
    // a provision once for each step that changed it. It also counts a change that one act made
    // and the next act undid.
    var kept = false;
    if (preparation.texts().get(page.from) instanceof Text.Ready before
        && preparation.texts().get(page.to) instanceof Text.Ready after) {
      var found = stepCounts.find(page.act.id(), before.root(), after.root());
      var counted = found.or(() -> count(page, preparation, page.from, page.to, new HashMap<>()));
      kept = found.isPresent();
      if (counted.isPresent()) {
        if (!kept) {
          stepCounts.add(page.act.id(), before.root(), after.root(), counted.get());
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
        together = counted.get().together();
        unknown = counted.get().unknown();
      }
    }
    LOG.info(
        "Acts of {} from {} to {} in {}: {} of {} counted{}, in {} ms",
        page.act.id(),
        page.from.date(),
        page.to.date(),
        page.language.code(),
        counts.size(),
        between.size(),
        kept ? " (kept)" : "",
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return templates
        .catalogueActs(
            CompareView.Acts.of(
                    page.act, page.from, page.to, page.language, page.only, view, counts, null)
                .counted(together, unknown))
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
      Page page,
      Preparation preparation,
      Version from,
      Version to,
      Map<ContentHash, Outline> outlines) {
    return attributed(page, preparation, from, to, null, outlines)
        .map(
            attributed -> {
              var differences = new HashMap<ActIdentifier, Integer>();
              page.act.integratedBetween(from, to).forEach(each -> differences.put(each, 0));
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
      Page page,
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
    var amended = page.act.amended(from, to, before.eIds(), outline.eIds(), page.language);
    var sidenotes = new IdentityHashMap<Difference, SidenoteView>();
    for (var difference : comparison.get().differences()) {
      sidenotes.put(
          difference,
          SidenoteView.of(
              page.act,
              from,
              to,
              difference,
              amended.of(difference.eId(), difference.kind() != Kind.CHANGED),
              only,
              page.language));
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
   * Returns the units of a Synopsis fold, which opens in place.
   *
   * <p>Each unit is a row. The row shows the unit of the later text in both columns, because the
   * unit is the same in both texts. The row also shows what last changed the unit.
   *
   * <p>Returns 404 if the address is not the canonical address of the comparison, or if the later
   * text is not built yet.
   */
  @GetMapping(path = PATH + "/same", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String same(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam String first,
      @RequestParam String last) {
    var page =
        new Page(new Eli(type, year, number), code, from, to, null, CompareView.View.SYNOPSIS);
    if (!page.at(from, to, null)) {
      throw Requests.notFound();
    }
    var started = System.nanoTime();
    if (!(texts.preparation(page.act, page.language).texts().get(page.to)
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
                SidenoteView.history(page.act, page.to, neighbour.markers(), page.language)));
      }
    }
    LOG.info(
        "Same units of {} from {} to {} in {}: {} of {}, in {} ms",
        page.act.id(),
        page.from.date(),
        page.to.date(),
        page.language.code(),
        rows.size(),
        eIds.size(),
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return templates.catalogueNeighbours(rows, this.html).render();
  }

  /**
   * Tries again to build the texts of the act, when the failure panel posts Try again to the
   * comparison page. It tries again only the texts that failed where a new try may help. Then it
   * redirects to the page, which prepares the act again.
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
    var page =
        new Page(new Eli(type, year, number), code, from, to, null, CompareView.View.CHANGES);
    texts.retry(page.act, page.language);
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(Links.compare(page.eli, page.from, page.to, page.language)))
        .build();
  }

  /**
   * Returns the address of the page, with its query. For example, {@code
   * /eli/reg_impl/2015/1998/eng/compare?from=…&amp;to=…}.
   */
  private static String self(HttpServletRequest request) {
    return request.getRequestURI() + "?" + request.getQueryString();
  }

  /**
   * The data that a comparison page shows:
   *
   * <ul>
   *   <li>the act
   *   <li>the two versions that apply on the dates asked for, in the language. From is always the
   *       earlier one. Without dates, they are the version that applies today and the version
   *       before it.
   *   <li>the act asked for, to limit the comparison to its changes, if that act is between the two
   *       versions
   * </ul>
   */
  private final class Page {
    final Eli eli;
    final Language language;
    final Act act;
    final Timeline timeline;
    final Version from;
    final Version to;
    final @Nullable ActIdentifier only;
    final CompareView.View view;

    Page(
        Eli eli,
        String code,
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        @Nullable String only,
        CompareView.View view) {
      this.eli = eli;
      this.view = view;
      language = Requests.language(code);
      act = acts.find(eli).orElseThrow(Requests::notFound);
      timeline = act.timeline().in(language).orElseThrow(Requests::notFound);
      // Without dates, use the version that applies today. If none applies today, use the latest.
      var current = timeline.applicableOn(Requests.today(clock)).orElseGet(timeline::latest);
      var later =
          to != null
              ? timeline.applicableOn(to).orElseThrow(Requests::notFound)
              : from == null && only != null ? integrating(only).orElse(current) : current;
      // Without From, if To is the first version, compare it with the version after it.
      if (to != null && from == null && timeline.predecessor(later).isEmpty()) {
        later = timeline.successor(later).orElse(later);
      }
      var earlier =
          from == null
              ? timeline.predecessor(later).orElse(later)
              : timeline.applicableOn(from).orElseThrow(Requests::notFound);
      var swapped = earlier.date().isAfter(later.date());
      this.from = swapped ? later : earlier;
      this.to = swapped ? earlier : later;
      this.only =
          only == null
              ? null
              : CompareView.between(act, this.from, this.to).stream()
                  .filter(each -> each.celex().equals(only))
                  .findFirst()
                  .orElse(null);
    }

    /**
     * Returns whether {@code from}, {@code to} and {@code only} are the canonical address of this
     * comparison. That is, the first days of its two versions, and an act between them, if any.
     */
    boolean at(@Nullable LocalDate from, @Nullable LocalDate to, @Nullable String only) {
      return this.from.date().equals(from)
          && this.to.date().equals(to)
          && Objects.equals(only, this.only == null ? null : this.only.celex());
    }

    /**
     * Returns the first version in the language that integrates the act {@code celex}. A comparison
     * limited to the changes of that act goes there if it has no From and no To. For example, the
     * "What it changed in …" link of a search goes there.
     */
    private Optional<Version> integrating(String celex) {
      return timeline.versions().stream()
          .filter(
              v ->
                  act.integratedBetween(earlier(v), v).stream()
                      .anyMatch(each -> each.celex().equals(celex)))
          .findFirst();
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

    /**
     * Returns what the page says if both ends are the same version. It says so, and links to the
     * page of the version. It also links to the comparison of the version with the version before
     * it, if there is one.
     */
    JteModel same() {
      return templates.catalogueSame(
          "Both ends are the same version ("
              + to.celex()
              + ", from "
              + Wording.date(to.date())
              + ").",
          timeline
              .predecessor(to)
              .map(before -> Links.compare(eli, before, to, language))
              .orElse(null),
          Links.read(eli, to, language));
    }

    /**
     * Returns the changes, after the act is prepared in the language.
     *
     * <ul>
     *   <li>If no text of the act could be built, it returns the failure panel of the act. The
     *       panel links back to the last search of the reader, {@code results}, if there is one.
     *   <li>If either text could not be built, it returns the failure panel of that text. Try again
     *       posts to {@code self}.
     *   <li>If both texts are built, it returns the Changes view, which loads from beside the page.
     *   <li>Otherwise, either version appeared after the act was prepared. It says that the text is
     *       being prepared.
     * </ul>
     */
    JteModel changes(Preparation preparation, String self, Chrome.@Nullable Results results) {
      if (preparation.ready() == 0) {
        return templates.catalogueTextFailed(FailureView.act(act, preparation, self, results));
      }
      // Both texts are in the language, because the versions come from the timeline in it.
      var earlier = Objects.requireNonNull(preparation.texts().get(from));
      var later = Objects.requireNonNull(preparation.texts().get(to));
      if (earlier instanceof Text.Failed failed) {
        return templates.catalogueTextFailed(
            FailureView.comparing(act, from, to, from, failed, language, self));
      }
      if (later instanceof Text.Failed failed) {
        return templates.catalogueTextFailed(
            FailureView.comparing(act, from, to, to, failed, language, self));
      }
      if (earlier instanceof Text.Ready && later instanceof Text.Ready) {
        return templates.catalogueCompareReady(Links.changes(eli, from, to, language, only, view));
      }
      // Either end is a version that appeared since, and it is being prepared.
      return templates.catalogueTextArriving();
    }
  }
}
