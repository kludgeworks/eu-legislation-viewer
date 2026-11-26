// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Today;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Assembles the collection representation of all versions of an act in the API. */
@Component
class VersionsModelAssembler implements RepresentationModelAssembler<Act, RepresentationModel<?>> {

  private final Today today;

  VersionsModelAssembler(Today today) {
    this.today = today;
  }

  /**
   * Returns the collection representation of all versions of an act, with each version embedded as
   * {@code item}, {@code self}, {@code up}, {@code first}, and {@code latest-version} if one
   * applies today.
   */
  @Override
  public RepresentationModel<?> toModel(Act act) {
    var celex = act.id();
    var model =
        HalModelBuilder.halModel()
            .embed(
                act.timeline().versions().stream()
                    .map(version -> ApiActResources.summary(celex, version))
                    .toList(),
                IanaLinkRelations.ITEM)
            .link(linkTo(methodOn(ApiVersionController.class).versions(celex)).withSelfRel())
            .link(ApiActResources.up(celex))
            .link(ApiActResources.to(celex, act.timeline().first(), IanaLinkRelations.FIRST));
    ApiActResources.latest(act, today.today()).ifPresent(model::link);
    return model.build();
  }
}
