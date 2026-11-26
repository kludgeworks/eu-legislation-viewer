// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActAddress;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NoSuchAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NotCatalogued;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Reading;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Readings;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Today;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.DivergenceNotesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ProvisionView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TagsView;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Shows the page of a version of an act, in a language.
 *
 * <p>The address has the ELI of the act, a date, and the EU code of the language, as an ELI has
 * them. For example, {@code /eli/reg_impl/2015/1998/2026-03-22/eng}. The date can be any date. The
 * page shows the version that applies on that date.
 *
 * <p>The act is prepared whole in the language, with every version, before the page shows a text.
 * Until then, the page is the preparing page, which becomes this page in place. Then the text of
 * the version loads from the content module, with its outline beside it. If the text could not be
 * built, the page shows why in its place. Where a new try may help, Try again posts to the page.
 *
 * <p>Versions that appeared after the act was prepared are prepared separately. The page does not
 * wait for them, but it says that they are being prepared.
 *
 * <p>The address of the act without a date redirects to the history of the act, if it has amending
 * acts. If not, or if the address has a date in {@code on}, it redirects to the page for today or
 * for that date. The address keeps the day asked: it is never moved to the first day of a version.
 *
 * <p>A day before the act's first version shows a panel that says the act didn't exist yet, or
 * wasn't in force yet.
 */
/*
 * A handler takes the act and the language of its address as an ActAddress, which
 * ActAddressResolver gives. The check knows only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
@Controller
class VersionController {
  private static final String PATH =
      "/eli/{type}/{year}/{number}/{date:\\d{4}-\\d{2}-\\d{2}}/{language}";

  private final Readings readings;
  private final Today today;
  private final Templates templates;

  VersionController(Readings readings, Today today, Templates templates) {
    this.readings = readings;
    this.today = today;
    this.templates = templates;
  }

  /**
   * Redirects to the first page of an act, in a language. An act that has amending acts opens on
   * its history. An act that no act changed opens on the law on today. If {@code on} is given, it
   * redirects to the law on that date. The date form of the Reading card sends {@code on}.
   */
  @GetMapping(path = "/eli/{type}/{year}/{number}/{language}", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<Void> act(ActAddress act, @Valid @ModelAttribute ActRequest asked) {
    var language = act.language();
    var eli = act.eli();
    var on = asked.on();
    var amended = readings.amended(eli, language);
    return ActLookupPages.seeOther(
        on == null && amended
            ? Links.history(eli, null, language)
            : Links.read(eli, on != null ? on : today.today(), language));
  }

  /**
   * Returns the page of the version.
   *
   * <p>While the act is being prepared in the language, it returns the preparing page. When the act
   * is prepared, that page requests this address again with {@code prepared}. The page's content
   * then fades in.
   */
  @GetMapping(path = PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String page(ActAddress act, @Valid @ModelAttribute DayRequest day, Frame frame) {
    var asked = new Asked(act, day.date(), frame);
    return switch (readings
        .of(act.eli(), act.language(), day.date())
        .orElseThrow(Requests::notFound)) {
      case Reading.BeforeTheAct before -> before(before, asked);
      case Reading.NotInTheLanguage missing -> missing(missing, asked);
      case Reading.Preparing preparing -> preparing(preparing, asked);
      case Reading.Prepared ready -> prepared(ready, asked, Boolean.TRUE.equals(day.prepared()));
    };
  }

  /**
   * Returns the history of one provision of the text of the version: the acts that changed it, with
   * the state of each change on the date. {@code occurrence} is the eId of the occurrence of the
   * text. The Read page loads it under the provision. For example, {@code
   * /eli/reg/2008/767/2026-11-17/eng/provision?occurrence=art_6/par_2}.
   */
  @GetMapping(path = PATH + "/provision", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String provision(ActAddress act, @Valid @ModelAttribute ProvisionRequest asked) {
    return readings
        .provision(act.eli(), act.language(), asked.date(), asked.occurrence())
        .map(provision ->
            templates.catalogueProvision(ProvisionView.of(provision, today.today())).render())
        .orElseThrow(Requests::notFound);
  }

  /**
   * Tries again to build the texts of the act, when the failure panel posts Try again to the
   * version page. It tries again only the texts that failed where a new try may help. Then it
   * redirects to the page, which prepares the act again.
   */
  @PostMapping(path = PATH)
  ResponseEntity<Void> retry(ActAddress act, @Valid @ModelAttribute DayRequest day) {
    readings.retry(act.eli(), act.language());
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(Links.read(act.eli(), day.date(), act.language())))
        .build();
  }

  /**
   * Returns the page of a day before the act's first version. Its card shows the day and a dash,
   * and a panel in place of the text says that the act didn't exist yet, or wasn't in force yet.
   */
  private String before(Reading.BeforeTheAct before, Asked asked) {
    var overTime = before.overTime();
    return templates
        .catalogueVersion(
            asked.frame(),
            MastheadView.reading(overTime, before.version(), asked.date()),
            ReadingView.before(overTime, asked.date()),
            templates.catalogueTextFailed(
                FailureView.before(overTime.act(), asked.date(), asked.act().language())),
            false,
            null)
        .render();
  }

  /** Returns the page of an act that has no version in the language. */
  private String missing(Reading.NotInTheLanguage missing, Asked asked) {
    var overTime = missing.overTime();
    var version = missing.version();
    return templates
        .catalogueVersion(
            asked.frame(),
            MastheadView.reading(overTime, version, asked.date()),
            ReadingView.of(
                overTime, version, asked.date(), missing.today(), List.of(), missing.law()),
            templates.catalogueTextFailed(
                FailureView.missing(overTime.act(), version, asked.act().language(), asked.self())),
            false,
            null)
        .render();
  }

  /** Returns the preparing page, which becomes the version page in place. */
  private String preparing(Reading.Preparing preparing, Asked asked) {
    var act = preparing.overTime().act();
    return templates
        .cataloguePreparing(
            asked.frame(),
            ReadingView.title(act, asked.date()),
            MastheadView.reading(preparing.overTime(), preparing.version(), asked.date())
                .whilePreparing(),
            PreparingView.of(
                act,
                preparing.preparation(),
                "You asked for the law on " + Dates.inWords(asked.date()),
                asked.self(),
                preparing.now()))
        .render();
  }

  /** Returns the version page of an act that is prepared in the language. */
  private String prepared(Reading.Prepared ready, Asked asked, boolean arrived) {
    var overTime = ready.overTime();
    var version = ready.version();
    return templates
        .catalogueVersion(
            asked.frame(),
            MastheadView.reading(overTime, version, asked.date()),
            ReadingView.of(
                overTime, version, asked.date(), ready.today(), ready.named(), ready.law()),
            text(ready, asked),
            arrived,
            ArrivingView.of(ready.preparation()).orElse(null))
        .render();
  }

  /**
   * Returns the text of the version, after the act is prepared.
   *
   * <ul>
   *   <li>If the version is not in the language, it returns the failure panel for a missing
   *       version.
   *   <li>If no text of the act could be built, it returns the failure panel of the act. The panel
   *       links back to the last search of the reader, if there is one.
   *   <li>If the text is built, it returns the text, with the divergence notes beside it where it
   *       is not the law on the day, {@link DivergenceNotesView}.
   *   <li>If only this text failed, it returns the failure panel of the version. Try again posts to
   *       the page.
   *   <li>If the version appeared after the act was prepared, it says that the text is being
   *       prepared.
   * </ul>
   */
  private JteModel text(Reading.Prepared ready, Asked asked) {
    var overTime = ready.overTime();
    var act = overTime.act();
    var version = ready.version();
    var preparation = ready.preparation();
    var text = preparation.texts().get(version);
    if (text == null) {
      // The version is not in the language.
      return templates.catalogueTextFailed(
          FailureView.missing(act, version, asked.act().language(), asked.self()));
    }
    if (preparation.ready() == 0) {
      return templates.catalogueTextFailed(
          FailureView.act(act, preparation, asked.self(), asked.frame().results()));
    }
    return switch (text) {
      case Text.Ready built ->
          templates.catalogueTextReady(
              "/content/" + built.text(),
              Objects.requireNonNull(ready.outline()),
              TagsView.of(overTime, version),
              DivergenceNotesView.of(
                  act, ready.law(), ready.text(), asked.act().language(), ready.today()),
              asked.self() + "/provision",
              ready.provisions(),
              asked.self(),
              version.eurLex(asked.act().language()).toString());
      // The version appeared since, and it is being prepared.
      case Text.Waiting _, Text.Converting _ -> templates.catalogueTextArriving();
      case Text.Failed failed ->
          templates.catalogueTextFailed(
              FailureView.reading(act, version, failed, asked.act().language(), asked.self()));
    };
  }

  /**
   * What the address of the first page of an act asks for, beside the act and the language.
   *
   * @param on the day that the date form of the Reading card sends, or null
   */
  record ActRequest(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate on) {}

  /**
   * What the address of the page of the law on a day asks for, beside the act and the language.
   *
   * @param date the day, from the path
   * @param prepared true if the preparing page asks again, now that the act is prepared, or null
   */
  record DayRequest(
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @Nullable Boolean prepared) {}

  /**
   * What the address of the history of one provision asks for, beside the act and the language.
   *
   * @param date the day of the text, from the path
   * @param occurrence the eId of the occurrence of the text
   */
  record ProvisionRequest(
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @NotNull String occurrence) {}

  /**
   * What the reader asked for: the act and the language of the address, the day, and the frame of
   * the page.
   */
  private record Asked(ActAddress act, LocalDate date, Frame frame) {

    /** Returns the address of the page, without its query. */
    String self() {
      return Links.read(act.eli(), date, act.language());
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
