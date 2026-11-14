// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.UriTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The root of the API, where a program starts. It finds an act by one of the links here, and every
 * other resource by a link from the act. Thus it never builds an address itself.
 *
 * <p>A resource of the API has one representation, HAL. A request that accepts {@code
 * application/hal+json}, {@code application/json} or anything gets it, labelled {@code
 * application/hal+json} by {@code ApiConfiguration}. A request that accepts only a type that is not
 * JSON gets 406.
 */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiRootController {
  /** The path of the root of the API. */
  static final String ROOT = "/api";

  /**
   * The IANA relation of the documentation for people, RFC 8631. Spring HATEOAS does not list it.
   */
  private static final LinkRelation SERVICE_DOC = LinkRelation.of("service-doc");

  /**
   * Returns the root:
   *
   * <ul>
   *   <li>{@code av:act}: an act by its CELEX number.
   *   <li>{@code av:act-by-celex} and {@code av:act-by-eli}: the lookups, which redirect to the
   *       act.
   *   <li>{@code service-doc}: the documentation for people.
   * </ul>
   */
  @GetMapping(ROOT)
  RepresentationModel<?> root() {
    var self = linkTo(methodOn(ApiRootController.class).root()).withSelfRel();
    return new RepresentationModel<>()
        .add(self)
        .add(template(self, "/acts/{celex}", Relation.ACT).withTitle("An act by its CELEX number"))
        .add(
            template(self, "/acts{?celex}", Relation.ACT_BY_CELEX)
                .withTitle("Look an act up by a CELEX number, of the act or of a version"))
        .add(
            template(self, "/acts{?eli}", Relation.ACT_BY_ELI)
                .withTitle("Look an act up by its ELI"))
        .add(
            Link.of(self.getHref() + "/docs", SERVICE_DOC)
                .withType(MediaType.TEXT_HTML_VALUE)
                .withTitle("The documentation of the API"));
  }

  /**
   * Returns a link whose address is a URI template of RFC 6570: the address of {@code from}, then
   * {@code template}. For example, {@code /acts/{celex}} or {@code /acts{?eli}}.
   */
  static Link template(Link from, String template, Relation relation) {
    return Link.of(UriTemplate.of(from.getHref() + template), relation.rel());
  }
}
