// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.code;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.History;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiHistoryController.HistoryModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representations of an act's history on a day in the API. */
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
}
