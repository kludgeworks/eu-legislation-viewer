// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * An act at its CELEX, as HAL (ActAssembler), as application/hal+json or plain application/json; an
 * act by its ELI is a lookup, answered 303 with the act's URL. Cacheable as long as the viewer
 * keeps an act (ActResources), an ELI's act never changing. A CELEX or an ELI no act has is 404.
 */
@RestController
@RequestMapping(
    path = "/api/acts",
    produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ActController {
  private final Acts acts;
  private final ActResources resources;
  private final ActAssembler assembler;

  ActController(Acts acts, ActResources resources, ActAssembler assembler) {
    this.acts = acts;
    this.resources = resources;
    this.assembler = assembler;
  }

  @GetMapping("/{act}")
  ResponseEntity<RepresentationModel<?>> act(@PathVariable("act") ActIdentifier id) {
    return resources.ok(assembler.toModel(resources.act(id)));
  }

  /** The act with an ELI: 303 to it. */
  @GetMapping(params = "eli")
  ResponseEntity<Void> byEli(@RequestParam("eli") String path) {
    Eli eli;
    try {
      eli = new Eli(path);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not an ELI: " + path, e);
    }
    var act =
        acts.find(eli)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "No act has the ELI " + eli));
    return resources.seeOther(ActLinks.act(act.id(), IanaLinkRelations.SELF));
  }
}
