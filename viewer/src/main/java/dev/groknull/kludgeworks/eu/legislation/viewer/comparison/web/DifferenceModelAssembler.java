// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Ends;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.DifferenceModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.DifferenceSummary;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.ApiLinks;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representation of a single difference in the API. */
@Component
class DifferenceModelAssembler {

  /** The JSON Schema of a difference, in version 1 of the API. */
  static final String DIFFERENCE_SCHEMA = "/api/schemas/v1/difference.schema.json";

  private final Content content;
  private final ComparisonModelAssembler comparisonAssembler;

  DifferenceModelAssembler(Content content, ComparisonModelAssembler comparisonAssembler) {
    this.content = content;
    this.comparisonAssembler = comparisonAssembler;
  }

  /**
   * Returns the representation of a single difference at {@code eId}: links to its canonical
   * address, {@code up} (the comparison), and {@code describedby} schema.
   */
  public EntityModel<DifferenceModel> toModel(
      Ends ends, Difference difference, Amended amended, String eId) {
    var comparison = comparisonAssembler.at(ends, IanaLinkRelations.UP);
    return EntityModel.of(
        new DifferenceModel(
            DifferenceSummary.of(ends.act(), difference, amended),
            content.publicWording(difference.shown()),
            content.publicOccurrence(difference.shown())),
        Link.of(ComparisonModelAssembler.differenceHref(comparison, eId)),
        comparison,
        ApiLinks.schema(DIFFERENCE_SCHEMA));
  }
}
