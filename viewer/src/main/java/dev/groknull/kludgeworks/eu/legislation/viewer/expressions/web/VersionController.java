// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.ActLinks;
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
 * An act's versions (URLs and API: public API): the list, each with its link; one version at the
 * date it applies from, language-neutral, with links to its text in each language Cellar has it in
 * (by language tag), to the act (version-history) and to the versions either side and the latest
 * (RFC 5829); and the version that applies on any date, a lookup answered 303 with that version's
 * URL. A date that starts no version, or one before the act's text first applies, is 404. HAL, as
 * application/hal+json or plain application/json; cacheable as long as the act is kept
 * (viewer.acts.cache-for).
 */
@RestController
@RequestMapping(
    path = "/api/acts/{act}/versions",
    produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class VersionController {
  static final HalLinkRelation VERSIONS = ApiRelations.of("versions");
  static final HalLinkRelation TEXT = ApiRelations.of("text");

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

  @GetMapping(params = "!on")
  ResponseEntity<RepresentationModel<?>> versions(@PathVariable("act") ActIdentifier id) {
    var act = act(id);
    var model =
        HalModelBuilder.emptyHalModel()
            .link(
                ActLinks.version(
                    id, act.timeline().latest().date(), IanaLinkRelations.LATEST_VERSION))
            .link(ActLinks.act(id, IanaLinkRelations.UP))
            .link(ActLinks.versions(id, IanaLinkRelations.SELF).expand())
            .embed(
                act.timeline().versions().stream().map(v -> ActLinks.summary(id, v)).toList(),
                VERSIONS)
            .build();
    return ResponseEntity.ok().cacheControl(cacheControl).body(model);
  }

  /** The version that applies on {@code date}: 303 to it. */
  @GetMapping(params = "on")
  ResponseEntity<Void> versionOn(
      @PathVariable("act") ActIdentifier id,
      @RequestParam("on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    var version =
        act(id)
            .versionOn(date)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "The text of " + id + " first applies later"));
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(ActLinks.version(id, version.date(), IanaLinkRelations.SELF).toUri())
        .cacheControl(cacheControl)
        .build();
  }

  @GetMapping("/{date:[0-9]{4}-[0-9]{2}-[0-9]{2}}")
  ResponseEntity<RepresentationModel<?>> version(
      @PathVariable("act") ActIdentifier id,
      @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    var act = act(id);
    var timeline = act.timeline();
    var version =
        timeline.versions().stream()
            .filter(v -> v.date().equals(date))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No version of "
                            + id
                            + " starts on "
                            + date
                            + "; versions?on="
                            + date
                            + " gives the one that applies"));
    var links = new ArrayList<Link>();
    links.add(ActLinks.version(id, date, IanaLinkRelations.SELF));
    links.add(ActLinks.act(id, IanaLinkRelations.VERSION_HISTORY));
    timeline
        .predecessor(version)
        .ifPresent(
            v -> links.add(ActLinks.version(id, v.date(), IanaLinkRelations.PREDECESSOR_VERSION)));
    timeline
        .successor(version)
        .ifPresent(v -> links.add(ActLinks.version(id, v.date(), SUCCESSOR_VERSION)));
    links.add(ActLinks.version(id, timeline.latest().date(), IanaLinkRelations.LATEST_VERSION));
    for (var language : expressions.languages(version.celex())) {
      links.add(ActLinks.text(id, date, language, TEXT).withHreflang(language.iso()));
    }
    var model = HalModelBuilder.halModelOf(VersionRepresentation.of(version)).links(links).build();
    return ResponseEntity.ok().cacheControl(cacheControl).body(model);
  }

  private Act act(ActIdentifier id) {
    return acts.find(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No act " + id));
  }
}
