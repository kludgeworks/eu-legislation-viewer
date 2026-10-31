// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.ActLinks;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.EliPaths;
import dev.groknull.kludgeworks.eu.legislation.viewer.api.ApiRelations;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * An act as it stood on a date (URLs and API: ELI's dated version, without a language): the version
 * that applies on it, with links to its text in each language Cellar has it in, to the act
 * (version-history) and to the versions either side and the latest (RFC 5829). Any date resolves to
 * its version, which the response names in Content-Location; a date before the act's text first
 * applies, or an ELI no act has, is 404. HAL, as application/hal+json or plain application/json;
 * cacheable as long as the act is kept (viewer.acts.cache-for).
 */
@RestController
class VersionController {
  static final HalLinkRelation TEXT = ApiRelations.of("text");
  static final String DATE = "/{date:[0-9]{4}-[0-9]{2}-[0-9]{2}}";

  /**
   * RFC 5829's successor-version. Not IanaLinkRelations.SUCCESSOR_VERSION: Spring HATEOAS (3.1.2)
   * spells it "successor-versions", a relation no registry has.
   */
  static final LinkRelation SUCCESSOR_VERSION = LinkRelation.of("successor-version");

  private final Acts acts;
  private final Expressions expressions;
  private final CacheControl cacheControl;

  /**
   * @param cacheFor how long an act is kept, and so may be cached (viewer.acts.cache-for)
   */
  VersionController(
      Acts acts, Expressions expressions, @Value("${viewer.acts.cache-for}") Duration cacheFor) {
    this.acts = acts;
    this.expressions = expressions;
    this.cacheControl = CacheControl.maxAge(cacheFor).cachePublic();
  }

  @GetMapping(
      path = {EliPaths.ACT + DATE, EliPaths.TREATY + DATE},
      produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
  ResponseEntity<RepresentationModel<?>> version(
      @RequestAttribute(EliPaths.ELI) Eli eli,
      @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    var act =
        acts.find(eli)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "No act has the ELI " + eli));
    var version =
        act.versionOn(date)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "The text of " + eli + " first applies later"));
    var self = ActLinks.version(eli, version.date()).withSelfRel();
    var links = new ArrayList<Link>();
    links.add(self);
    links.add(ActLinks.act(eli).withRel(IanaLinkRelations.VERSION_HISTORY));
    var timeline = act.timeline();
    timeline
        .predecessor(version)
        .ifPresent(v -> links.add(link(eli, v, IanaLinkRelations.PREDECESSOR_VERSION)));
    timeline.successor(version).ifPresent(v -> links.add(link(eli, v, SUCCESSOR_VERSION)));
    links.add(link(eli, timeline.latest(), IanaLinkRelations.LATEST_VERSION));
    for (var language : expressions.languages(version.celex())) {
      links.add(
          ActLinks.expression(eli, version.date(), language)
              .withRel(TEXT)
              .withHreflang(language.iso()));
    }
    var model = HalModelBuilder.halModelOf(VersionRepresentation.of(version)).links(links).build();
    var response = ResponseEntity.ok().cacheControl(cacheControl);
    if (!version.date().equals(date)) {
      response.header(HttpHeaders.CONTENT_LOCATION, self.getHref());
    }
    return response.body(model);
  }

  private static Link link(Eli eli, Version version, LinkRelation relation) {
    return ActLinks.version(eli, version.date()).withRel(relation);
  }
}
