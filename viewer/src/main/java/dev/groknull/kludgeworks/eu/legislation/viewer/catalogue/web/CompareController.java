// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActAddress;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparedLaw;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparedLaws;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NoSuchAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NotCatalogued;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline.TwoDays;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shows the Compare page of an act: the law on two days, in a language.
 *
 * <p>For example, {@code /eli/reg_impl/2015/1998/eng/compare?from=2016-02-29&amp;to=2017-06-03}.
 * Each end can be any day. The page shows the two days as the reader gave them.
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
 * <p>{@link ComparedLaws} tells which of its cases the page is in. This controller shows the case:
 * it decides nothing about the law.
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
/*
 * A handler takes the act and the language of its address as an ActAddress, which
 * ActAddressResolver gives. The check knows only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
@Controller
class CompareController {
  private final ComparedLaws compared;
  private final Templates templates;

  CompareController(ComparedLaws compared, Templates templates) {
    this.compared = compared;
    this.templates = templates;
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
      ActAddress act, @Valid @ModelAttribute CompareRequest page, Frame frame) {
    var asked = new Asked(act, page, frame);
    return switch (compared
        .of(act.eli(), act.language(), page.from(), page.to(), page.occurrence())
        .orElseThrow(Requests::notFound)) {
      case ComparedLaw.Elsewhere elsewhere ->
          ActLookupPages.seeOther(asked.address(elsewhere.days()));
      case ComparedLaw.BeforeTheAct before -> html(before(before, asked));
      case ComparedLaw.Preparing preparing -> html(preparing(preparing, asked));
      case ComparedLaw.Prepared ready ->
          html(prepared(ready, asked, Boolean.TRUE.equals(page.prepared())));
    };
  }

  /**
   * Tries again to build the texts of the act, when the failure panel posts Try again to the
   * compare page. It tries again only the texts that failed where a new try may help. Then it
   * redirects to the page, which prepares the act again.
   */
  @PostMapping(path = ComparePage.PATH)
  ResponseEntity<Void> retry(ActAddress act, @Valid @ModelAttribute CompareRequest page) {
    var eli = act.eli();
    var language = act.language();
    var from = page.from();
    var to = page.to();
    if (from == null || to == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A comparison needs from and to.");
    }
    var days = compared.retry(eli, language, from, to).orElseThrow(Requests::notFound);
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(
            URI.create(
                new ComparePage(Links.compare(eli, language), days.from(), days.to(), null, null)
                    .href()))
        .build();
  }

  /** Returns the page of a comparison whose From is a day before the first text of the act. */
  private String before(ComparedLaw.BeforeTheAct before, Asked asked) {
    var days = before.days();
    return templates
        .catalogueCompare(
            asked.frame(),
            title(before.overTime(), days),
            asked.masthead(before.overTime(), days),
            CompareView.before(before.overTime(), days.from(), days.to()),
            templates.catalogueTextFailed(
                FailureView.comparingBefore(
                    before.overTime().act(),
                    days.from(),
                    before.afterFirst(),
                    asked.act().language())),
            false,
            null,
            asked.page().view())
        .render();
  }

  /** Returns the preparing page, which becomes the compare page in place. */
  private String preparing(ComparedLaw.Preparing preparing, Asked asked) {
    var days = preparing.days();
    return templates
        .cataloguePreparing(
            asked.frame(),
            title(preparing.overTime(), days),
            asked.masthead(preparing.overTime(), days).whilePreparing(),
            PreparingView.of(
                preparing.overTime().act(),
                preparing.preparation(),
                "You asked for a comparison of the law on "
                    + Dates.inWords(days.from())
                    + " with the law on "
                    + Dates.inWords(days.to()),
                asked.frame().here(),
                preparing.now()))
        .render();
  }

  /** Returns the compare page of an act that is prepared in the language. */
  private String prepared(ComparedLaw.Prepared ready, Asked asked, boolean arrived) {
    var days = ready.days();
    return templates
        .catalogueCompare(
            asked.frame(),
            title(ready.overTime(), days),
            asked.masthead(ready.overTime(), days),
            CompareView.of(
                ready.compared(), days.from(), days.to(), asked.page().occurrence(), ready.today()),
            ready.earlier().equals(ready.later())
                ? templates.catalogueCompareSame(
                    "The wording is the same on the two days.",
                    Links.read(asked.act().eli(), days.to(), asked.act().language()),
                    "Read the law on " + Dates.inWords(days.to()))
                : differences(ready, asked),
            arrived,
            ArrivingView.of(ready.preparation()).orElse(null),
            asked.page().view())
        .render();
  }

  /**
   * Returns the differences slot of an act that is prepared in the language.
   *
   * <ul>
   *   <li>If no text of the act could be built, it returns the failure panel of the act. The panel
   *       links back to the last search of the reader, if there is one.
   *   <li>If either text could not be built, it returns the failure panel of that text. Try again
   *       posts to the page.
   *   <li>If both texts are built, it returns the slot that loads the differences from the
   *       comparison module. For one provision, the slot loads the wording of the provision on the
   *       two days.
   *   <li>Otherwise, either version appeared after the act was prepared. It says that the text is
   *       being prepared.
   * </ul>
   */
  private JteModel differences(ComparedLaw.Prepared ready, Asked asked) {
    var act = ready.overTime().act();
    var preparation = ready.preparation();
    var from = ready.earlier();
    var to = ready.later();
    var days = ready.days();
    if (preparation.ready() == 0) {
      return templates.catalogueTextFailed(
          FailureView.act(act, preparation, asked.frame().here(), asked.frame().results()));
    }
    // Both texts are in the language, because the versions come from the timeline in it.
    var earlier = Objects.requireNonNull(preparation.texts().get(from));
    var later = Objects.requireNonNull(preparation.texts().get(to));
    if (earlier instanceof Text.Failed failed) {
      return templates.catalogueTextFailed(
          FailureView.comparing(
              act, from, to, from, failed, asked.act().language(), asked.frame().here()));
    }
    if (later instanceof Text.Failed failed && earlier instanceof Text.Ready) {
      return templates.catalogueTextFailed(
          FailureView.comparing(
              act, from, to, to, failed, asked.act().language(), asked.frame().here()));
    }
    if (!(earlier instanceof Text.Ready) || !(later instanceof Text.Ready)) {
      // The version appeared since, and it is being prepared.
      return templates.catalogueTextArriving();
    }
    var fragments =
        Links.page(asked.act().eli(), from, to, asked.act().language(), null, asked.page().view())
            .on(days.from(), days.to());
    return templates.catalogueCompareReady(
        asked.page().occurrence() == null
            ? fragments.differences()
            : fragments.sides(List.of(asked.page().occurrence())),
        "Comparing the law on "
            + Dates.inWords(days.from())
            + " with the law on "
            + Dates.inWords(days.to())
            + "…");
  }

  private static String title(ActOverTime overTime, TwoDays days) {
    return overTime.act().id().cited()
        + " · "
        + Dates.inShort(days.from())
        + " → "
        + Dates.inShort(days.to());
  }

  private static ResponseEntity<String> html(String page) {
    return ResponseEntity.ok().contentType(Requests.HTML).body(page);
  }

  /**
   * What the address of a compare page asks for, beside the act and the language.
   *
   * @param from the earlier day, or null
   * @param to the later day, or null
   * @param view the comparison module's view, as the reader gave it, or null
   * @param occurrence the eId of the provision that the page is limited to, or null
   * @param prepared true if the preparing page asks again, now that the act is prepared, or null
   */
  record CompareRequest(
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
      @Nullable String view,
      @Nullable String occurrence,
      @Nullable Boolean prepared) {}

  /**
   * What the reader asked for: the act and the language of the address, the parameters of the page,
   * and the frame of the page.
   */
  private record Asked(ActAddress act, CompareRequest page, Frame frame) {

    /** Returns the address of the page at {@code days}, with its view or its provision. */
    String address(TwoDays days) {
      var occurrence = page.occurrence();
      var at =
          new ComparePage(
              Links.compare(act.eli(), act.language()), days.from(), days.to(), null, page.view());
      return occurrence == null ? at.href() : at.href(occurrence);
    }

    MastheadView masthead(ActOverTime overTime, TwoDays days) {
      return MastheadView.comparing(overTime, days.from(), days.to(), page.occurrence());
    }
  }

  /** Answers a page whose act the catalogue does not have yet with the page that waits for it. */
  @ExceptionHandler(NotCatalogued.class)
  ResponseEntity<String> notCatalogued(NotCatalogued notCatalogued, Frame frame) {
    return ActLookupPages.lookingUp(templates, notCatalogued, frame);
  }

  /** Answers a page whose address no act has with the page that says so, with 404. */
  @ExceptionHandler(NoSuchAct.class)
  ResponseEntity<String> missing(NoSuchAct missing, Frame frame) {
    return ActLookupPages.missing(templates, missing, frame);
  }
}
