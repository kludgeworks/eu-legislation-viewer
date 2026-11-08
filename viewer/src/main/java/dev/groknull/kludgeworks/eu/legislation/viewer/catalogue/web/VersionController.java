// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.Links;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Requests;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import gg.jte.models.runtime.JteModel;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.web.server.ResponseStatusException;

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
 * <p>The address of the act without a date redirects to the page of the version that applies today,
 * or to the page for the date in {@code on}.
 */
@Controller
class VersionController {
  private static final Logger LOG = LoggerFactory.getLogger(VersionController.class);

  private static final String PATH =
      "/eli/{type}/{year}/{number}/{date:\\d{4}-\\d{2}-\\d{2}}/{language}";

  private final Acts acts;
  private final Texts texts;
  private final Content content;
  private final Templates templates;
  private final Clock clock;

  VersionController(Acts acts, Texts texts, Content content, Templates templates, Clock clock) {
    this.acts = acts;
    this.texts = texts;
    this.content = content;
    this.templates = templates;
    this.clock = clock;
  }

  /**
   * Redirects to the page of the version of the act that applies today, in a language. The Read
   * link uses this address.
   *
   * <p>The redirect goes to the first day of the version, so that a bookmark of it keeps pointing
   * at that version. Before the act applies, it goes to the first version. If {@code on} is given,
   * it redirects to the page for that date. The date form of the Reading card sends {@code on}.
   */
  @GetMapping(path = "/eli/{type}/{year}/{number}/{language}", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<Void> act(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate on) {
    var language = language(code);
    var eli = new Eli(type, year, number);
    var act = acts.find(eli).orElseThrow(VersionController::notFound);
    var date =
        on != null
            ? on
            : act.versionOn(today()).orElseGet(() -> act.timeline().versions().getFirst()).date();
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(Links.read(eli, date, language)))
        .build();
  }

  /**
   * Returns the page of the version.
   *
   * <p>While the act is being prepared in the language, it returns the preparing page. When the act
   * is prepared, that page requests this address again with {@code prepared}. The page then shows a
   * receipt that says what was prepared.
   */
  @GetMapping(path = PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String page(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable("language") String code,
      @RequestParam(defaultValue = "false") boolean prepared,
      HttpServletRequest request) {
    var page = new Page(type, year, number, date, code);
    var chrome = Chrome.of(request);
    var masthead = MastheadView.reading(page.act, page.version, date, page.language);
    if (page.act.timeline().in(page.language).isEmpty()) {
      return page.render(
          chrome,
          masthead,
          templates.catalogueTextFailed(
              FailureView.missing(page.act, page.version, page.language, request.getRequestURI())),
          null,
          Map.of(),
          null,
          List.of());
    }
    var preparation = texts.prepare(page.act, page.language);
    if (preparation.preparing()) {
      return templates
          .cataloguePreparing(
              chrome,
              page.version.celex(),
              masthead.whilePreparing(),
              TimelineView.preparing(page.act, preparation),
              PreparingView.of(
                  page.act,
                  preparation,
                  "You asked for the version that applies on " + Dates.inWords(date),
                  request.getRequestURI(),
                  clock.instant()))
          .render();
    }
    return page.render(
        chrome,
        masthead,
        page.text(preparation, request.getRequestURI(), chrome.results()),
        prepared ? PreparingView.receipt(preparation, page.language) : null,
        TimelineView.states(preparation),
        ArrivingView.of(preparation).orElse(null),
        page.named(preparation));
  }

  /**
   * Tries again to build the texts of the act, when the failure panel posts Try again to the
   * version page. It tries again only the texts that failed where a new try may help. Then it
   * redirects to the page, which prepares the act again.
   */
  @PostMapping(path = PATH)
  ResponseEntity<Void> retry(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable("language") String code,
      HttpServletRequest request) {
    var page = new Page(type, year, number, date, code);
    texts.retry(page.act, page.language);
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(request.getRequestURI()))
        .build();
  }

  /**
   * The data that a version page shows: the act, the version that applies on the date, and the
   * language.
   */
  private final class Page {
    final Eli eli;
    final Act act;
    final Version version;
    final LocalDate date;
    final Language language;

    Page(String type, int year, String number, LocalDate date, String code) {
      language = language(code);
      eli = new Eli(type, year, number);
      act = acts.find(eli).orElseThrow(VersionController::notFound);
      version = act.versionOn(date).orElseThrow(VersionController::notFound);
      this.date = date;
    }

    String render(
        Chrome chrome,
        MastheadView masthead,
        JteModel text,
        @Nullable String receipt,
        Map<Version, String> states,
        @Nullable ArrivingView arriving,
        List<ActIdentifier> named) {
      return templates
          .catalogueVersion(
              chrome,
              version,
              masthead,
              TimelineView.reading(act, version, language, states),
              ReadingView.of(act, version, date, language, today(), named),
              text,
              receipt,
              arriving)
          .render();
    }

    /**
     * Returns the text of the version, after the act is prepared.
     *
     * <ul>
     *   <li>If the version is not in the language, it returns the failure panel for a missing
     *       version.
     *   <li>If no text of the act could be built, it returns the failure panel of the act. The
     *       panel links back to the last search of the reader, {@code results}, if there is one.
     *   <li>If the text is built, it returns the text.
     *   <li>If only this text failed, it returns the failure panel of the version. Try again posts
     *       to {@code self}.
     *   <li>If the version appeared after the act was prepared, it says that the text is being
     *       prepared.
     * </ul>
     */
    JteModel text(Preparation preparation, String self, Chrome.@Nullable Results results) {
      var text = preparation.texts().get(version);
      if (text == null) {
        // The version is not in the language.
        return templates.catalogueTextFailed(FailureView.missing(act, version, language, self));
      }
      if (preparation.ready() == 0) {
        return templates.catalogueTextFailed(FailureView.act(act, preparation, self, results));
      }
      return switch (text) {
        case Text.Ready ready ->
            templates.catalogueTextReady(
                "/content/" + ready.root().hex(),
                content.outline(ready.root()).orElseGet(Outline::empty),
                Links.compare(eli, language));
        // The version appeared since, and it is being prepared.
        case Text.Queued _, Text.Building _ -> templates.catalogueTextArriving();
        case Text.Failed failed ->
            templates.catalogueTextFailed(
                FailureView.reading(act, version, failed, language, self));
      };
    }

    /**
     * Returns the acts that the text of the version names, but the text of the version before it
     * does not name. The text names these acts in the list of integrated acts in its Formex. That
     * list is kept in the lifecycle of the text.
     *
     * <p>Returns none in these cases:
     *
     * <ul>
     *   <li>The version is the original version.
     *   <li>The Publications Office lists acts that the version integrates.
     *   <li>There is no version before it.
     *   <li>Either text is not built.
     * </ul>
     */
    List<ActIdentifier> named(Preparation preparation) {
      if (version.kind() == Version.Kind.ORIGINAL || !version.integrates().isEmpty()) {
        return List.of();
      }
      var before = act.timeline().predecessor(version);
      if (before.isEmpty()
          || !(preparation.texts().get(version) instanceof Text.Ready text)
          || !(preparation.texts().get(before.get()) instanceof Text.Ready earlier)) {
        return List.of();
      }
      var named = new ArrayList<>(listed(text.root()));
      named.removeAll(listed(earlier.root()));
      LOG.info(
          "{} lists no amending act; its text names {} more than {}'s",
          version.celex(),
          named,
          before.get().celex());
      return named;
    }

    /**
     * Returns the acts that the text of {@code root} lists as integrated. These are the amendments
     * in its lifecycle.
     */
    private List<ActIdentifier> listed(ContentHash root) {
      return content.read(root).stream()
          .flatMap(text -> text.children().stream())
          .filter(child -> child.name().equals("lifecycle"))
          .flatMap(lifecycle -> lifecycle.children().stream())
          .filter(event -> "amendment".equals(event.attribute("type")))
          .map(event -> event.attribute("act"))
          .filter(Objects::nonNull)
          .filter(ActIdentifier::isCelex)
          .map(ActIdentifier::new)
          .distinct()
          .toList();
    }
  }

  /** Returns today, in Brussels. */
  private LocalDate today() {
    return Requests.today(clock);
  }

  private static Language language(String code) {
    return Requests.language(code);
  }

  private static ResponseStatusException notFound() {
    return Requests.notFound();
  }
}
