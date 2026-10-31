// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.api.ApiRelations;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
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
 * An act at its CELEX (URLs and API: public API): its identifiers and dates, its versions embedded
 * as summaries with their links (never the text), the versions resource (with {@code ?on} the one
 * that applies on a date), the first version (IANA's first, RFC 8288) and the latest, comparing two
 * versions and its amendments (both planned: 501, ApiProblems), and its counterparts on EUR-Lex and
 * Cellar as alternates (Links back to EUR-Lex). An act by its ELI is a lookup, answered 303 with
 * the act's URL. HAL, as application/hal+json or plain application/json. Cacheable as long as the
 * viewer keeps an act (viewer.acts.cache-for): a timeline changes only when Cellar publishes a
 * consolidation, and an ELI's act never. A CELEX or an ELI no act has is 404.
 */
@RestController
@RequestMapping(
    path = "/api/acts",
    produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ActController {
  static final HalLinkRelation VERSIONS = ApiRelations.of("versions");
  static final HalLinkRelation COMPARE = ApiRelations.of("compare");
  static final HalLinkRelation AMENDMENTS = ApiRelations.of("amendments");

  /** The language of EUR-Lex's page an act links to, until a request says which it wants. */
  static final Language EUR_LEX_LANGUAGE = Language.ENG;

  private final Acts acts;
  private final URI cellarResources;
  private final CacheControl cacheControl;

  /**
   * @param cellarResources where Cellar's resource URIs start (viewer.cellar.resources)
   * @param cacheFor how long an act is kept, and so may be cached (viewer.acts.cache-for)
   */
  ActController(
      Acts acts,
      @Value("${viewer.cellar.resources}") URI cellarResources,
      @Value("${viewer.acts.cache-for}") Duration cacheFor) {
    this.acts = acts;
    this.cellarResources = cellarResources;
    this.cacheControl = CacheControl.maxAge(cacheFor).cachePublic();
  }

  @GetMapping("/{act}")
  ResponseEntity<RepresentationModel<?>> act(@PathVariable("act") ActIdentifier id) {
    var act =
        acts.find(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No act " + id));
    var model =
        HalModelBuilder.halModelOf(ActRepresentation.of(act))
            .link(ActLinks.act(id, IanaLinkRelations.SELF))
            .link(ActLinks.versions(id, VERSIONS))
            .link(ActLinks.version(id, act.timeline().first().date(), IanaLinkRelations.FIRST))
            .link(
                ActLinks.version(
                    id, act.timeline().latest().date(), IanaLinkRelations.LATEST_VERSION))
            .link(ActLinks.compare(id, COMPARE))
            .link(ActLinks.amendments(id, AMENDMENTS))
            .link(eurLex(act))
            .link(cellar(act))
            .embed(
                act.timeline().versions().stream().map(v -> ActLinks.summary(id, v)).toList(),
                VERSIONS)
            .build();
    return ResponseEntity.ok().cacheControl(cacheControl).body(model);
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
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(ActLinks.act(act.id(), IanaLinkRelations.SELF).toUri())
        .cacheControl(cacheControl)
        .build();
  }

  /** The act's document information page on EUR-Lex, by its CELEX. */
  private static Link eurLex(Act act) {
    var language = EUR_LEX_LANGUAGE.iso();
    return Link.of(
            "https://eur-lex.europa.eu/legal-content/"
                + language.toUpperCase(Locale.ROOT)
                + "/ALL/?uri=CELEX:"
                + act.id().celex(),
            IanaLinkRelations.ALTERNATE)
        .withName(ActLinks.EUR_LEX)
        .withType(MediaType.TEXT_HTML_VALUE)
        .withHreflang(language)
        .withTitle("View on EUR-Lex");
  }

  /** The act's work in Cellar, by its CELEX; a treaty's slash encoded, as Cellar has it. */
  private Link cellar(Act act) {
    return Link.of(
            cellarResources.resolve("celex/" + act.id().celex().replace("/", "%2F")).toString(),
            IanaLinkRelations.ALTERNATE)
        .withName(ActLinks.CELLAR)
        .withTitle("The work in Cellar");
  }
}
