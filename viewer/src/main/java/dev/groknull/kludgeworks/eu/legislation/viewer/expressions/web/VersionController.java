// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.ActLinks;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.ActResources;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
 * An act's versions (URLs and API: public API), as HAL (VersionAssembler): the list, and one
 * version at the date it applies from; and the version that applies on any date, a lookup answered
 * 303 with that version's URL. A date that starts no version, or one before the act's text first
 * applies, is 404. As application/hal+json or plain application/json; cacheable as long as the act
 * is kept (ActResources).
 */
@RestController
@RequestMapping(
    path = "/api/acts/{act}/versions",
    produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class VersionController {
  private final ActResources resources;
  private final VersionAssembler assembler;

  VersionController(ActResources resources, VersionAssembler assembler) {
    this.resources = resources;
    this.assembler = assembler;
  }

  @GetMapping(params = "!on")
  ResponseEntity<RepresentationModel<?>> versions(@PathVariable("act") ActIdentifier id) {
    return resources.ok(assembler.toCollectionModel(resources.act(id)));
  }

  /** The version that applies on {@code date}: 303 to it. */
  @GetMapping(params = "on")
  ResponseEntity<Void> versionOn(
      @PathVariable("act") ActIdentifier id,
      @RequestParam("on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    var version =
        resources
            .act(id)
            .versionOn(date)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "The text of " + id + " first applies later"));
    return resources.seeOther(ActLinks.version(id, version.date(), IanaLinkRelations.SELF));
  }

  @GetMapping("/{date:[0-9]{4}-[0-9]{2}-[0-9]{2}}")
  ResponseEntity<RepresentationModel<?>> version(
      @PathVariable("act") ActIdentifier id,
      @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    var act = resources.act(id);
    return resources.ok(assembler.toModel(act, resources.version(act, date)));
  }
}
