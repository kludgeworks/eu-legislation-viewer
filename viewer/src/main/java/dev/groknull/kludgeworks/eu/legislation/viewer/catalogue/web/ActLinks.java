// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.ApiLinks;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.LinkBuilder;
import org.springframework.hateoas.server.core.AbstractEntityLinks;
import org.springframework.stereotype.Component;

/**
 * Links to an act in the API, for the other modules. A module asks Spring HATEOAS's {@code
 * EntityLinks} for {@code linkToItemResource(Act.class, celex)}, with the act's {@link
 * ActIdentifier}, and gets the address of {@link ApiActController#act}. Thus it links to an act
 * without knowing the catalogue's controllers.
 *
 * <p>The link comes from the controller's mapping, so the {@code /} of a CELEX number is {@code
 * %2F}, as on the act's own links. The acts have no collection resource: the collection link is the
 * lookup by CELEX number, a URI template.
 */
@Component
class ActLinks extends AbstractEntityLinks {

  @Override
  public boolean supports(Class<?> type) {
    return Act.class.equals(type);
  }

  @Override
  public LinkBuilder linkFor(Class<?> type) {
    return linkTo(methodOn(ApiRootController.class).root()).slash("acts");
  }

  @Override
  public LinkBuilder linkFor(Class<?> type, Object... parameters) {
    return linkFor(type);
  }

  @Override
  public LinkBuilder linkForItemResource(Class<?> type, Object id) {
    return linkTo(methodOn(ApiActController.class).act(ActIdentifier.class.cast(id)));
  }

  @Override
  public Link linkToCollectionResource(Class<?> type) {
    var root = linkTo(methodOn(ApiRootController.class).root()).withSelfRel();
    return ApiLinks.template(root, "/acts{?celex}", Relation.ACT_BY_CELEX.rel());
  }

  @Override
  public Link linkToItemResource(Class<?> type, Object id) {
    return linkForItemResource(type, id).withSelfRel();
  }
}
