// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.stereotype.Component;

/**
 * Adds the lookup of its comparisons to an act in the API, {@code av:comparisons}. The act is the
 * catalogue's resource. The comparison module depends on the catalogue, not the reverse, so it adds
 * its link to the catalogue's resource, as RESTBucks adds payment links to an order.
 */
@Component
class ActComparisonLinks implements RepresentationModelProcessor<EntityModel<Act>> {
  /** The relation of the lookup of an act's comparisons, which the viewer's CURIE documents. */
  static final HalLinkRelation COMPARISONS = HalLinkRelation.curied("av", "comparisons");

  @Override
  public EntityModel<Act> process(EntityModel<Act> model) {
    var act = model.getContent();
    if (act == null) {
      return model;
    }
    var lookup =
        linkTo(methodOn(ApiComparisonController.class).lookup(act.id(), null, null, null, null))
            .toUriComponentsBuilder()
            .replaceQuery(null)
            .toUriString();
    return model.add(
        Link.of(UriTemplate.of(lookup + "{?from,to,language,amendingAct}"), COMPARISONS));
  }
}
