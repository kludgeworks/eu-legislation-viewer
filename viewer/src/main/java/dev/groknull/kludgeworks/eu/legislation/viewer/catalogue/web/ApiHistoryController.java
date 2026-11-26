// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.code;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Histories;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.History;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.LegalState;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The history of an act on a day, in the API. It shows the same {@link History} as the history
 * page: the state of the act with the facts that give it, and how each amending act stands.
 */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiHistoryController {
  private final Histories histories;

  ApiHistoryController(Histories histories) {
    this.histories = histories;
  }

  /** Returns the history of the act on {@code day}, by its changes to the text in a language. */
  @GetMapping(ACT + "/history/{language}/{day}")
  RepresentationModel<?> history(
      @PathVariable ActIdentifier celex,
      @PathVariable String language,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
    var history = histories.of(celex, Requests.language(language), day);
    return HalModelBuilder.halModelOf(HistoryModel.of(history))
        .link(
            linkTo(methodOn(ApiHistoryController.class).history(celex, language, day))
                .withSelfRel())
        .link(ApiActResources.up(celex))
        .build();
  }

  /**
   * Returns the states of the act itself for each day, and how each amending act stands for each
   * day. The history of a day is read from the same answer.
   */
  @GetMapping(ACT + "/history/{language}")
  RepresentationModel<?> overTime(
      @PathVariable ActIdentifier celex, @PathVariable String language) {
    var overTime = histories.overTime(celex, Requests.language(language));
    return HalModelBuilder.halModelOf(
            new OverTimeModel(
                overTime.state().periods().stream()
                    .map(period ->
                        new PeriodModel<>(
                            period.from(), period.until(), StateModel.of(period.value())))
                    .toList(),
                overTime.amendingActs().stream()
                    .map(each ->
                        new AmendingOverTimeModel(
                            each.act().celex(),
                            each.stands().periods().stream()
                                .map(period ->
                                    new PeriodModel<>(
                                        period.from(),
                                        period.until(),
                                        code(period.value().state().name())))
                                .toList()))
                    .toList()))
        .link(linkTo(methodOn(ApiHistoryController.class).overTime(celex, language)).withSelfRel())
        .link(ApiActResources.up(celex))
        .build();
  }

  /**
   * An act for each day, as the API shows it.
   *
   * @param state the states of the act itself, in order
   * @param amendingActs each amending act, newest first, with its states in order
   */
  record OverTimeModel(
      List<PeriodModel<StateModel>> state, List<AmendingOverTimeModel> amendingActs) {}

  /**
   * A period.
   *
   * @param from its first day, or null if it has no start
   * @param until the first day of the next period, or null if it has no end
   * @param value what is true in it
   */
  record PeriodModel<T>(@Nullable LocalDate from, @Nullable LocalDate until, T value) {}

  /**
   * An amending act for each day.
   *
   * @param celex its CELEX number
   * @param state its state as a whole, in periods
   */
  record AmendingOverTimeModel(String celex, List<PeriodModel<String>> state) {}

  /**
   * The history of an act on a day, as the API shows it.
   *
   * @param day the day
   * @param state the state of the act itself, with the facts that give it
   * @param amendingActs each amending act, newest first, as it stands on the day
   */
  record HistoryModel(LocalDate day, @Nullable StateModel state, List<AmendingModel> amendingActs) {

    static HistoryModel of(History history) {
      var state = history.state();
      return new HistoryModel(
          history.day(),
          state == null ? null : StateModel.of(state),
          history.amendingActs().stream().map(AmendingModel::of).toList());
    }
  }

  /**
   * The state of an act on a day, with the facts that give it. A fact that does not belong to the
   * state is null.
   *
   * @param state the state, for example {@code not_applying}
   * @param endOfValidity the end of validity, for an act that is no longer in force
   * @param repealedBy the acts that repealed it
   * @param entryIntoForce its entry into force, for an act that is not in force yet
   * @param appliesFrom the day from which it applies, if it applies from one day
   * @param appliesInFullFrom the day from which the whole act applies, for an act that applies in
   *     part
   * @param notKnownBecause why the data does not tell the state
   */
  record StateModel(
      String state,
      @Nullable LocalDate endOfValidity,
      List<String> repealedBy,
      @Nullable LocalDate entryIntoForce,
      @Nullable LocalDate appliesFrom,
      @Nullable LocalDate appliesInFullFrom,
      @Nullable String notKnownBecause) {

    static StateModel of(LegalState state) {
      var name = code(state.state().name());
      return switch (state) {
        case LegalState.NoLongerInForce ended ->
            new StateModel(
                name,
                ended.endOfValidity(),
                ended.repealedBy().stream().map(ActIdentifier::celex).toList(),
                null,
                null,
                null,
                null);
        case LegalState.NotInForce before ->
            new StateModel(name, null, List.of(), before.entryIntoForce(), null, null, null);
        case LegalState.NotApplying not ->
            new StateModel(name, null, List.of(), null, from(not.applying()), null, null);
        case LegalState.InPart part ->
            new StateModel(name, null, List.of(), null, null, part.inFullFrom(), null);
        case LegalState.Applies applies ->
            new StateModel(name, null, List.of(), null, from(applies.applying()), null, null);
        case LegalState.NotKnown unknown ->
            new StateModel(name, null, List.of(), null, null, null, code(unknown.why().name()));
      };
    }

    private static @Nullable LocalDate from(Applying applying) {
      return applying instanceof Applying.From one ? one.day() : null;
    }
  }

  /**
   * An amending act, as it stands on the day.
   *
   * @param celex its CELEX number
   * @param state its state as a whole
   * @param applies how many of its changes apply
   * @param notApplying how many do not apply yet
   * @param notKnown how many have a state that the data does not tell
   * @param changes how many changes it makes to the text in the language
   */
  record AmendingModel(
      String celex, String state, int applies, int notApplying, int notKnown, int changes) {

    static AmendingModel of(History.AmendingAct amending) {
      var stands = amending.stands();
      return new AmendingModel(
          amending.act().celex(),
          code(stands.state().name()),
          stands.applies(),
          stands.notApplying(),
          stands.notKnown(),
          amending.changes().size());
    }
  }
}
