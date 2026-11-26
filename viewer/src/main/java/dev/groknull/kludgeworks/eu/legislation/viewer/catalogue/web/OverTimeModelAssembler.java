// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.code;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.AmendingOverTimeModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.OverTimeModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.PeriodModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.StateModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/**
 * Assembles the hypermedia representation of what the rules tell of an act over time in the API.
 */
@Component
class OverTimeModelAssembler
    implements RepresentationModelAssembler<ActOverTime, EntityModel<OverTimeModel>> {

  /**
   * Returns the representation of what the rules tell of an act for each day: the periods of each
   * state of the act itself, and how each amending act stands over time, linked with {@code self},
   * {@code up} and {@code av:on}, the history of a day.
   */
  @Override
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
    var self = linkTo(methodOn(ApiHistoryController.class).overTime(celex, language)).withSelfRel();
    return EntityModel.of(
        model,
        self,
        ApiRootController.template(self, "/on/{day}", Relation.ON),
        ApiActResources.up(celex));
  }
}
