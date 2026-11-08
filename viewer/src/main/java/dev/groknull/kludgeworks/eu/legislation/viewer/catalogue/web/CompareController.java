// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Requests;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
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

/**
 * Shows the page of an act in Compare mode: two versions of the act, in a language.
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
 * <p>The page is the catalogue's: the act's masthead, its timeline, the From and To cards, and the
 * states of its preparation. What the comparison found is the comparison module's. The page has two
 * slots for it, which htmx fills from that module's addresses ({@link ComparePage#changes}, {@link
 * ComparePage#acts}, from {@link Links#page}) once both texts are built: the chips of the acts
 * between the versions, and the changes. The page passes {@code view} on to them as the reader gave
 * it. It does not know the views.
 *
 * <p>The act is prepared whole in the language, with every version, before the page shows changes.
 * Until then, the page is the preparing page, which becomes this page in place.
 */
@Controller
class CompareController {
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

  /**
   * Returns the compare page, or redirects to the canonical address of the comparison.
   *
   * <p>While the act is being prepared in the language, it returns the preparing page. When the act
   * is prepared, that page requests this address again with {@code prepared}. The page then shows a
   * receipt that says what was prepared.
   */
  @GetMapping(path = ComparePage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> page(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(name = ComparePage.FROM, required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @RequestParam(name = ComparePage.TO, required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to,
      @RequestParam(name = ComparePage.ACT, required = false) @Nullable String act,
      @RequestParam(name = ComparePage.VIEW, required = false) @Nullable String view,
      @RequestParam(defaultValue = "false") boolean prepared,
      @RequestParam(required = false) @Nullable String opened,
      @RequestParam(required = false) @Nullable String outside,
      HttpServletRequest request) {
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
                ? new ActIdentifier(outside).cited()
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
                              + Dates.inWords(page.from.date())
                              + " with the one that applies on "
                              + Dates.inWords(page.to.date()),
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
                            Links.page(page.eli, page.from, page.to, page.language, page.only, view)
                                .acts())
                        .noting(note),
                    page.from.equals(page.to)
                        ? page.same()
                        : page.changes(preparation, self(request), chrome.results()),
                    prepared ? PreparingView.receipt(preparation, page.language) : null,
                    ArrivingView.of(preparation).orElse(null))
                .render());
  }

  /**
   * Tries again to build the texts of the act, when the failure panel posts Try again to the
   * compare page. It tries again only the texts that failed where a new try may help. Then it
   * redirects to the page, which prepares the act again.
   */
  @PostMapping(path = ComparePage.PATH)
  ResponseEntity<Void> retry(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(ComparePage.FROM) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(ComparePage.TO) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    var page = new Page(new Eli(type, year, number), code, from, to, null, null);
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
   * The data that a compare page shows:
   *
   * <ul>
   *   <li>the act
   *   <li>the two versions that apply on the dates asked for, in the language. From is always the
   *       earlier one. Without dates, they are the version that applies today and the version
   *       before it.
   *   <li>the act asked for, to limit the comparison to its changes, if that act is between the two
   *       versions
   *   <li>the comparison module's view, as the reader gave it, or null
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
    final @Nullable String view;

    Page(
        Eli eli,
        String code,
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        @Nullable String only,
        @Nullable String view) {
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
              : act.integratedBetween(this.from, this.to).stream()
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
                  act.integratedBetween(timeline.predecessor(v).orElse(v), v).stream()
                      .anyMatch(each -> each.celex().equals(celex)))
          .findFirst();
    }

    /**
     * Returns what the page says if both ends are the same version. It says so, and links to the
     * page of the version. It also links to the comparison of the version with the version before
     * it, if there is one.
     */
    JteModel same() {
      return templates.catalogueCompareSame(
          "Both ends are the same version ("
              + to.celex()
              + ", from "
              + Dates.inWords(to.date())
              + ").",
          timeline
              .predecessor(to)
              .map(before -> Links.compare(eli, before, to, language))
              .orElse(null),
          Links.read(eli, to, language));
    }

    /**
     * Returns the changes slot, after the act is prepared in the language.
     *
     * <ul>
     *   <li>If no text of the act could be built, it returns the failure panel of the act. The
     *       panel links back to the last search of the reader, {@code results}, if there is one.
     *   <li>If either text could not be built, it returns the failure panel of that text. Try again
     *       posts to {@code self}.
     *   <li>If both texts are built, it returns the slot that loads the changes from the comparison
     *       module.
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
        return templates.catalogueCompareReady(
            Links.page(eli, from, to, language, only, view).changes());
      }
      // Either end is a version that appeared since, and it is being prepared.
      return templates.catalogueTextArriving();
    }
  }
}
