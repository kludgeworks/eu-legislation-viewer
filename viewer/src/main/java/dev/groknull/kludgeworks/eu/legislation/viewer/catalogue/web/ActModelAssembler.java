// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Today;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.ApiLinks;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/**
 * Assembles the hypermedia representation of an {@link Act} in the API: its self link, version
 * history, the latest applicable version today in Brussels, the date lookup template, its history
 * template, and its amendments.
 */
@Component
class ActModelAssembler implements RepresentationModelAssembler<Act, EntityModel<Act>> {
  private final Today today;

  ActModelAssembler(Today today) {
    this.today = today;
  }

  @Override
  public EntityModel<Act> toModel(Act act) {
    var celex = act.id();
    var self = linkTo(methodOn(ApiActController.class).act(celex)).withSelfRel();
    var model = EntityModel.of(act).add(self).add(ApiActResources.versionHistory(celex));
    ApiActResources.latest(act, today.today()).ifPresent(model::add);
    return model
        .add(ApiLinks.template(self, "/on/{day}", Relation.ON.rel()))
        .add(ApiLinks.template(self, "/history/texts/{language}", Relation.HISTORY.rel()))
        .add(
            linkTo(methodOn(ApiAmendmentsController.class).amendments(celex))
                .withRel(Relation.AMENDMENTS.rel()));
  }
}
