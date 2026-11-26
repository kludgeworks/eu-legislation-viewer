// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Ends;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.Preparing;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Assembles the representation of a comparison whose texts are still being prepared. */
@Component
class ComparisonPreparingModelAssembler
    implements RepresentationModelAssembler<Ends, RepresentationModel<?>> {

  private final ComparisonLinks links;

  ComparisonPreparingModelAssembler(ComparisonLinks links) {
    this.links = links;
  }

  /**
   * Returns the representation of a comparison whose texts are still being prepared: links to
   * {@code self} and {@code up} (the act).
   */
  @Override
  public RepresentationModel<?> toModel(Ends ends) {
    var celex = ends.act().id();
    var self = links.at(ends, IanaLinkRelations.SELF);
    return HalModelBuilder.halModelOf(Preparing.of(ends)).link(self).link(links.up(celex)).build();
  }
}
