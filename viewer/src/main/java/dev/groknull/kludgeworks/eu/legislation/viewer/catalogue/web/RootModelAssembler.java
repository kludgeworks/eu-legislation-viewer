// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.site.ApiLinks;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representation of the root of the API. */
@Component
class RootModelAssembler {

  /**
   * The IANA relation of the documentation for people, RFC 8631. Spring HATEOAS does not list it.
   */
  private static final LinkRelation SERVICE_DOC = LinkRelation.of("service-doc");

  /**
   * Returns the representation of the root. It has no content of its own: links to {@code self},
   * {@code av:act}, {@code av:act-by-celex}, {@code av:act-by-eli} (templates) and {@code
   * service-doc}.
   */
  public RepresentationModel<?> toModel() {
    var self = linkTo(methodOn(ApiRootController.class).root()).withSelfRel();
    return new RepresentationModel<>()
        .add(self)
        .add(
            ApiLinks.template(self, "/acts/{celex}", Relation.ACT.rel())
                .withTitle("An act by its CELEX number"))
        .add(
            ApiLinks.template(self, "/acts{?celex}", Relation.ACT_BY_CELEX.rel())
                .withTitle("Look an act up by a CELEX number, of the act or of a version"))
        .add(
            ApiLinks.template(self, "/acts{?eli}", Relation.ACT_BY_ELI.rel())
                .withTitle("Look an act up by its ELI"))
        .add(
            Link.of(self.getHref() + "/docs", SERVICE_DOC)
                .withType(MediaType.TEXT_HTML_VALUE)
                .withTitle("The documentation of the API"));
  }
}
