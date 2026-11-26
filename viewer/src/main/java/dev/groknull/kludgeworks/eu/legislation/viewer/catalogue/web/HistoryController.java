// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActAddress;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Histories;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NoSuchAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NotCatalogued;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.AmendingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HistoryView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Shows the history of an act, and the page of one of its amending acts.
 *
 * <p>The history lists the acts that amend the act, newest first, with the state of each on a day.
 * For example, {@code /eli/reg/2018/1862/eng/history?on=2022-08-01}. The page of an amending act
 * lists what that act changed, with the state of each change on the day. For example, {@code
 * /eli/reg/2018/1862/eng/history/32022R1190?on=2022-08-01}.
 *
 * <p>The day is today, unless the address gives one in {@code on}. Both pages need only the
 * catalogue. They do not wait for the texts of the act. The page of an amending act puts its
 * changes in the order of the text if a text of the act is built.
 */
/*
 * A handler takes the act and the language of its address as an ActAddress, which
 * ActAddressResolver gives. The check knows only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
@Controller
class HistoryController {
  private static final String HISTORY = "/eli/{type}/{year}/{number}/{language}/history";

  private final Histories histories;
  private final Templates templates;

  HistoryController(Histories histories, Templates templates) {
    this.histories = histories;
    this.templates = templates;
  }

  /**
   * Returns the history of the act on the day {@code on}. If {@code from} and {@code to} are given,
   * the history is of these two days: it shows the amending acts between them, and their state on
   * the later day.
   */
  @GetMapping(path = HISTORY, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String history(
      ActAddress act, @Valid @ModelAttribute HistoryRequest asked, HttpServletRequest request) {
    var history = histories.of(act.eli(), act.language(), asked.on(), asked.from(), asked.to());
    return templates
        .catalogueHistory(
            Frame.of(request),
            MastheadView.history(history.overTime(), history.day()),
            HistoryView.of(history))
        .render();
  }

  /**
   * Returns the page of the amending act {@code celex} on the day {@code on}. If {@code applies} is
   * given, the page shows only the changes that apply from that day.
   */
  @GetMapping(path = HISTORY + "/{celex}", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String amending(
      ActAddress act, @Valid @ModelAttribute AmendingRequest asked, HttpServletRequest request) {
    return Optional.of(asked.celex())
        .filter(ActIdentifier::isCelex)
        .map(ActIdentifier::new)
        .flatMap(amending -> histories.amending(act.eli(), amending, act.language(), asked.on()))
        .map(amending ->
            templates
                .catalogueAmending(
                    Frame.of(request),
                    MastheadView.history(amending.overTime(), amending.day()),
                    AmendingView.of(amending, asked.applies()))
                .render())
        .orElseThrow(Requests::notFound);
  }

  /**
   * What the address of the history of an act asks for, beside the act and the language.
   *
   * @param on the day of the history, or null for today
   * @param from the first of two days, or null
   * @param to the second of two days, or null
   */
  record HistoryRequest(
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate on,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {}

  /**
   * What the address of the page of an amending act asks for, beside the act and the language.
   *
   * @param celex the CELEX number of the amending act
   * @param on the day, or null for today
   * @param applies the day whose changes the page shows only, or null
   */
  record AmendingRequest(
      @NotNull String celex,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate on,
      @Nullable String applies) {}

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
