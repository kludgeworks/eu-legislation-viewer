// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LawOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Shows the Compare page of an act: the law on two days, in a language.
 *
 * <p>For example, {@code /eli/reg_impl/2015/1998/eng/compare?from=2016-02-29&amp;to=2017-06-03}.
 * Each end can be any day. The page shows the two days as the reader gave them. The text for a day
 * is the act's rule, {@link LawOnADay#textOn}.
 *
 * <p>With {@code occurrence}, the page is limited to one provision: the wording of the provision on
 * each of the two days, and the steps between them. The history of a provision on Read links there.
 *
 * <p>The page redirects to the canonical address of the comparison in these cases:
 *
 * <ul>
 *   <li>From is after To.
 *   <li>A day is missing.
 * </ul>
 *
 * <p>The two days are the timeline's, {@link Timeline#twoDays}.
 *
 * <p>The page is the catalogue's: the head of the act, the two days, the amending acts between
 * them, and the states of its preparation. What the comparison found is the comparison module's.
 * The page has a slot for it, which htmx fills from an address of that module ({@link
 * ComparePage#differences}, or {@link ComparePage#sides} for one provision) once both texts are
 * built. The page passes {@code view} on as the reader gave it. It does not know the views.
 *
 * <p>The act is prepared whole in the language before the page shows differences. Until then, the
 * page is the preparing page, which becomes this page in place.
 */
@Controller
class CompareController {
  private final Catalogued acts;
  private final Texts texts;
  private final Templates templates;
  private final Clock clock;

  CompareController(Catalogued acts, Texts texts, Templates templates, Clock clock) {
    this.acts = acts;
    this.texts = texts;
    this.templates = templates;
    this.clock = clock;
  }

  /**
   * Returns the compare page, or redirects to the canonical address of the comparison. That address
   * has both days, From before To. The days are kept as asked.
   *
   * <p>While the act is being prepared in the language, it returns the preparing page. When the act
   * is prepared, that page requests this address again with {@code prepared}. The page's content
   * then fades in.
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
      @RequestParam(name = ComparePage.VIEW, required = false) @Nullable String view,
      @RequestParam(name = ComparePage.OCCURRENCE, required = false) @Nullable String occurrence,
      @RequestParam(defaultValue = "false") boolean prepared,
      HttpServletRequest request) {
    var page = new Page(new Eli(type, year, number), code, from, to, view, occurrence);
    if (!page.at(from, to)) {
      return ActLookupPages.seeOther(page.address(), request);
    }
    var title =
        page.act.id().cited()
            + " · "
            + Dates.inShort(page.fromDay)
            + " → "
            + Dates.inShort(page.toDay);
    var frame = Frame.of(request);
    var masthead =
        MastheadView.comparing(page.act, page.fromDay, page.toDay, occurrence, page.language);
    var today = Timeline.today(clock);
    if (page.before) {
      return ResponseEntity.ok()
          .contentType(Requests.HTML)
          .body(
              templates
                  .catalogueCompare(
                      frame,
                      title,
                      masthead,
                      CompareView.before(page.act, page.fromDay, page.toDay, page.language),
                      templates.catalogueTextFailed(
                          FailureView.comparingBefore(
                              page.act, page.fromDay, page.afterFirst(), page.language)),
                      false,
                      null,
                      view)
                  .render());
    }
    var preparation = texts.prepare(page.act, page.language);
    if (preparation.preparing()) {
      return ResponseEntity.ok()
          .contentType(Requests.HTML)
          .body(
              templates
                  .cataloguePreparing(
                      frame,
                      title,
                      masthead.whilePreparing(),
                      PreparingView.of(
                          page.act,
                          preparation,
                          "You asked for a comparison of the law on "
                              + Dates.inWords(page.fromDay)
                              + " with the law on "
                              + Dates.inWords(page.toDay),
                          self(request),
                          clock.instant()))
                  .render());
    }
    var tags = texts.tags(preparation);
    var earlier = page.text(page.fromDay, tags);
    var later = page.text(page.toDay, tags);
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .body(
            templates
                .catalogueCompare(
                    frame,
                    title,
                    masthead,
                    CompareView.of(
                        page.act,
                        earlier,
                        page.fromDay,
                        later,
                        page.toDay,
                        occurrence,
                        page.language,
                        today,
                        tags),
                    earlier.equals(later)
                        ? templates.catalogueCompareSame(
                            "The wording is the same on the two days.",
                            Links.read(page.eli, page.toDay, page.language),
                            "Read the law on " + Dates.inWords(page.toDay))
                        : page.differences(
                            preparation, earlier, later, self(request), frame.results()),
                    prepared,
                    ArrivingView.of(preparation).orElse(null),
                    view)
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
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(page.address())).build();
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
   *   <li>the two days asked for, {@link Timeline#twoDays}. A day before the first version of the
   *       act has no text: the page then says that the act didn't exist yet, or wasn't in force
   *       yet.
   *   <li>the comparison module's view, as the reader gave it, or null
   *   <li>the provision that the page is limited to, or null
   * </ul>
   */
  private final class Page {
    final Eli eli;
    final Language language;
    final Act act;
    final Timeline timeline;
    final LocalDate fromDay;
    final LocalDate toDay;
    final boolean before;
    final @Nullable String view;
    final @Nullable String occurrence;

    Page(
        Eli eli,
        String code,
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        @Nullable String view,
        @Nullable String occurrence) {
      this.eli = eli;
      this.view = view;
      this.occurrence = occurrence;
      language = Requests.language(code);
      act = acts.act(eli);
      timeline = act.timeline().in(language).orElseThrow(Requests::notFound);
      var days = timeline.twoDays(from, to, Timeline.today(clock));
      fromDay = days.from();
      toDay = days.to();
      before = timeline.startsAfter(fromDay);
    }

    /** Returns whether {@code from} and {@code to} are the two days of this comparison. */
    boolean at(@Nullable LocalDate from, @Nullable LocalDate to) {
      return fromDay.equals(from) && toDay.equals(to);
    }

    /** Returns the address of the page, with its days, and its view or its provision. */
    String address() {
      var page = new ComparePage(Links.compare(eli, language), fromDay, toDay, null, view);
      return occurrence == null ? page.href() : page.href(occurrence);
    }

    /** Returns the version whose text shows the law on {@code day}, {@link LawOnADay#textOn}. */
    Version text(LocalDate day, Map<Version, TextTags> tags) {
      return LawOnADay.of(act).textOn(day, language, tags).orElseThrow(Requests::notFound);
    }

    /**
     * Returns the day that a comparison from the first version can end on, {@link
     * Timeline#endFromTheFirst}.
     */
    LocalDate afterFirst() {
      return timeline.endFromTheFirst(toDay);
    }

    /**
     * Returns the differences slot, after the act is prepared in the language. {@code from} and
     * {@code to} are the versions whose texts show the law on the two days.
     *
     * <ul>
     *   <li>If no text of the act could be built, it returns the failure panel of the act. The
     *       panel links back to the last search of the reader, {@code results}, if there is one.
     *   <li>If either text could not be built, it returns the failure panel of that text. Try again
     *       posts to {@code self}.
     *   <li>If both texts are built, it returns the slot that loads the differences from the
     *       comparison module. For one provision, the slot loads the wording of the provision on
     *       the two days.
     *   <li>Otherwise, either version appeared after the act was prepared. It says that the text is
     *       being prepared.
     * </ul>
     */
    JteModel differences(
        Preparation preparation,
        Version from,
        Version to,
        String self,
        Frame.@Nullable Results results) {
      if (preparation.ready() == 0) {
        return templates.catalogueTextFailed(FailureView.act(act, preparation, self, results));
      }
      // Both texts are in the language, because the versions come from the timeline in it.
      var earlier = Objects.requireNonNull(preparation.texts().get(from));
      var later = Objects.requireNonNull(preparation.texts().get(to));
      return switch (earlier) {
        case Text.Failed failed ->
            templates.catalogueTextFailed(
                FailureView.comparing(act, from, to, from, failed, language, self));
        case Text.Ready ready ->
            switch (later) {
              case Text.Failed failed ->
                  templates.catalogueTextFailed(
                      FailureView.comparing(act, from, to, to, failed, language, self));
              case Text.Ready alsoReady -> {
                var fragments = Links.page(eli, from, to, language, null, view).on(fromDay, toDay);
                yield templates.catalogueCompareReady(
                    occurrence == null
                        ? fragments.differences()
                        : fragments.sides(List.of(occurrence)),
                    "Comparing the law on "
                        + Dates.inWords(fromDay)
                        + " with the law on "
                        + Dates.inWords(toDay)
                        + "…");
              }
              // The version appeared since, and it is being prepared.
              case Text.Waiting waiting -> templates.catalogueTextArriving();
              case Text.Converting converting -> templates.catalogueTextArriving();
            };
        case Text.Waiting waiting -> templates.catalogueTextArriving();
        case Text.Converting converting -> templates.catalogueTextArriving();
      };
    }
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
