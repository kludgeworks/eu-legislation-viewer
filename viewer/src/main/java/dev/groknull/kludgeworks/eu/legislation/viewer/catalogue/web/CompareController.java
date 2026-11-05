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
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Wording;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
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
      @RequestParam(required = false) @Nullable String act,
      @RequestParam(name = "view", defaultValue = "changes") String shown,
      @RequestParam(defaultValue = "false") boolean prepared,
      HttpServletRequest request) {
    var view = view(shown);
    var page = new Page(new Eli(type, year, number), code, from, to, act, view);
    if (!page.at(from, to, act)) {
      return ResponseEntity.status(HttpStatus.SEE_OTHER)
          .location(
              URI.create(
                  Links.compare(page.eli, page.from, page.to, page.language, page.only, view)))
          .build();
    }
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
                        Links.acts(page.eli, page.from, page.to, page.language, page.only, view)),
                    page.changes(preparation, self(request)),
                    prepared ? PreparingView.receipt(preparation, page.language) : null,
                    ArrivingView.of(page.act, preparation, self(request)).orElse(null))
                .render());
  }

  /**
   * The comparison's Changes view, as its page loads it once both texts are built: the comparison
   * module's, worked out now if no one has yet, beside the later text's outline, each difference
   * with its sidenote, from its markers and Cellar's amendments of its unit (Act.amended). Limited
   * to an act's changes, the comparison of the version that integrates it with the one before (the
   * wording just before its change against just after it), the differences it made shown, the
   * others folded. 404 at an address that isn't the comparison's, or before its texts are.
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
                // and those that may be its, not known
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
                      all)
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
                      all)
                  .render();
          case SYNOPSIS -> {
            // the neighbours of the differences, each with what last made it as it is
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
                    all)
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
   * Whether a difference with {@code sidenote} is greyed: limited to {@code only}'s changes, one
   * that may be its, not known.
   */
  private static boolean greyed(@Nullable ActIdentifier only, @Nullable SidenoteView sidenote) {
    return only != null && sidenote != null && !sidenote.candidates().isEmpty();
  }

  /** The view a comparison's address asks for; 404 for one there isn't. */
  private static CompareView.View view(String code) {
    return CompareView.View.of(code).orElseThrow(Requests::notFound);
  }

  /**
   * The acts between the comparison's versions, as its page loads them: each with how many
   * differences it made, in the comparison of the version that integrates it with the one before,
   * by the same attribution as the sidenotes (Act.amended, the markers); an act whose version's
   * texts aren't both built left uncounted. Each step counted once and kept (StepCounts). 404 at an
   * address that isn't the comparison's.
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
    var outlines = new HashMap<ContentHash, Outline>();
    var steps = 0;
    var kept = 0;
    var together = 0;
    var unknown = 0;
    for (var version : page.timeline.versions()) {
      if (!version.date().isAfter(page.from.date()) || version.date().isAfter(page.to.date())) {
        continue;
      }
      var earlier = page.earlier(version);
      if (!(preparation.texts().get(earlier) instanceof Text.Ready before)
          || !(preparation.texts().get(version) instanceof Text.Ready after)) {
        continue;
      }
      var found = stepCounts.find(page.act.id(), before.root(), after.root());
      var counted = found.or(() -> count(page, preparation, earlier, version, outlines));
      if (counted.isEmpty()) {
        continue;
      }
      if (found.isPresent()) {
        kept++;
      } else {
        stepCounts.add(page.act.id(), before.root(), after.root(), counted.get());
      }
      steps++;
      counted
          .get()
          .differences()
          .forEach(
              (each, differences) -> {
                if (between.contains(each)) {
                  counts.merge(each, differences, Integer::sum);
                }
              });
      together += counted.get().together();
      unknown += counted.get().unknown();
    }
    LOG.info(
        "Acts of {} from {} to {} in {}: {} of {} counted over {} versions ({} kept), in {} ms",
        page.act.id(),
        page.from.date(),
        page.to.date(),
        page.language.code(),
        counts.size(),
        between.size(),
        steps,
        kept,
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return templates
        .catalogueActs(
            CompareView.Acts.of(
                    page.act, page.from, page.to, page.language, page.only, view, counts, null)
                .counted(together, unknown))
        .render();
  }

  /**
   * The counts of the step from {@code from} to {@code to}: how many differences each of its acts
   * made, how many acts made together, how many one of them made, not known which; none if either
   * text isn't built.
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
   * The comparison of {@code from} with {@code to}, each difference with its sidenote, limited to
   * {@code only}'s changes, if any, its outlines read once each ({@code outlines}, by root); none
   * if either text isn't built.
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
    var page =
        new Page(new Eli(type, year, number), code, from, to, null, CompareView.View.CHANGES);
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
   * language, From the earlier; without dates, the latest and the one before it; limited to the
   * changes of the act asked for, if it is one between them.
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
      this.only =
          only == null
              ? null
              : CompareView.between(act, this.from, this.to).stream()
                  .filter(each -> each.celex().equals(only))
                  .findFirst()
                  .orElse(null);
    }

    /**
     * Whether {@code from}, {@code to} and {@code only} are this comparison's address: its
     * versions' days, and an act between them, if any.
     */
    boolean at(@Nullable LocalDate from, @Nullable LocalDate to, @Nullable String only) {
      return this.from.date().equals(from)
          && this.to.date().equals(to)
          && Objects.equals(only, this.only == null ? null : this.only.celex());
    }

    /** The version in the language after From, up to To, that integrates {@code amending}. */
    Optional<Version> step(ActIdentifier amending) {
      return timeline.versions().stream()
          .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
          .filter(v -> act.integratedBetween(earlier(v), v).contains(amending))
          .findFirst();
    }

    /** The version in the language before {@code version}: the text just before its changes. */
    Version earlier(Version version) {
      return timeline.predecessor(version).orElse(version);
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
        return templates.catalogueCompareReady(Links.changes(eli, from, to, language, only, view));
      }
      // either end a version that appeared since, being prepared
      return templates.catalogueTextArriving();
    }
  }
}
