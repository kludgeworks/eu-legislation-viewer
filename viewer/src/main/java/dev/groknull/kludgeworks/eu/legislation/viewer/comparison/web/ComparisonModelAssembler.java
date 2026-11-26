// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.DIFFERENCE;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Ends;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.ComparisonModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.DifferenceSummary;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representation of a comparison between two versions in the API. */
@Component
class ComparisonModelAssembler {

  private final ComparisonLinks links;

  ComparisonModelAssembler(ComparisonLinks links) {
    this.links = links;
  }

  /**
   * Returns the representation of a comparison whole: each difference embedded as {@code item},
   * {@code self}, {@code up} (the act), and {@code av:difference} (URI template).
   */
  public RepresentationModel<?> toModel(Ends ends, Comparison comparison, Amended amended) {
    var celex = ends.act().id();
    var self = links.at(ends, IanaLinkRelations.SELF);
    return HalModelBuilder.halModelOf(ComparisonModel.of(ends, comparison))
        .embed(
            comparison.differences().stream()
                .map(difference ->
                    EntityModel.of(DifferenceSummary.of(ends.act(), difference, amended))
                        .add(Link.of(ComparisonLinks.differenceHref(self, difference.eId()))))
                .toList(),
            IanaLinkRelations.ITEM)
        .link(self)
        .link(links.up(celex))
        .link(Link.of(ComparisonLinks.differenceTemplate(self), DIFFERENCE))
        .build();
  }
}
