// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.code;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.History;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.AmendingOverTimeModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.HistoryModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.OverTimeModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.PeriodModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.StateModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representations of an act's history in the API. */
@Component
class HistoryModelAssembler
    implements RepresentationModelAssembler<History, EntityModel<HistoryModel>> {

  /**
   * Returns the representation of an act's history on a day: its state and each amending act as it
   * stands on that day, linked with {@code self} and {@code up}.
   */
  @Override
  public EntityModel<HistoryModel> toModel(History history) {
    var celex = history.act().id();
    var language = code(history.language().code());
    var day = history.day();
    return EntityModel.of(
        HistoryModel.of(history),
        linkTo(methodOn(ApiHistoryController.class).history(celex, language, day)).withSelfRel(),
        ApiActResources.up(celex));
  }

  /**
   * Returns the representation of what the rules tell of an act for each day: the periods of each
   * state of the act itself, and how each amending act stands over time, linked with {@code self}
   * and {@code up}.
   */
  public EntityModel<OverTimeModel> toModel(ActOverTime overTime) {
    var celex = overTime.act().id();
    var language = code(overTime.language().code());
    var model =
        new OverTimeModel(
            overTime.state().periods().stream()
                .map(period ->
                    new PeriodModel<>(period.from(), period.until(), StateModel.of(period.value())))
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
                .toList());
    return EntityModel.of(
        model,
        linkTo(methodOn(ApiHistoryController.class).overTime(celex, language)).withSelfRel(),
        ApiActResources.up(celex));
  }
}
