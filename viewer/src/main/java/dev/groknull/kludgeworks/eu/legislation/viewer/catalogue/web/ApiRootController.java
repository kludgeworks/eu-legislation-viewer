// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
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

  private final RootModelAssembler assembler;

  ApiRootController(RootModelAssembler assembler) {
    this.assembler = assembler;
  }

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
    return assembler.toModel();
  }
}
