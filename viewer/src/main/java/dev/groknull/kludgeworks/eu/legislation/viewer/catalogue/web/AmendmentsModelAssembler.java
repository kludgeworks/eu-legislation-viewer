// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.DateModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiAmendmentsController.AmendmentModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiAmendmentsController.AmendmentsModel;
import java.util.List;
import java.util.TreeMap;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representation of an act's amendments in the API. */
@Component
class AmendmentsModelAssembler
    implements RepresentationModelAssembler<Act, EntityModel<AmendmentsModel>> {

  /**
   * Returns the representation of an act's amendments: each amendment with its citations and dates,
   * linked with {@code self} and {@code up}.
   */
  @Override
  public EntityModel<AmendmentsModel> toModel(Act act) {
    var celex = act.id();
    var dates = new TreeMap<String, List<DateModel>>();
    act.dates()
        .amending()
        .forEach((amending, own) ->
            dates.put(amending.celex(), own.stream().map(DateModel::of).toList()));
    var model =
        new AmendmentsModel(
            act.amendments().stream().map(each -> AmendmentModel.of(act, each)).toList(), dates);
    return EntityModel.of(
        model,
        linkTo(methodOn(ApiAmendmentsController.class).amendments(celex)).withSelfRel(),
        ApiActResources.up(celex));
  }
}
