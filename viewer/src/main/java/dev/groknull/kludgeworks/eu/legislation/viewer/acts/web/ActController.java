// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * An act at its ELI (URLs and API): its identifiers and dates, its versions embedded (date, kind,
 * what each integrates; never the text), and its counterparts on EUR-Lex and Cellar as alternates
 * (Links back to EUR-Lex). Cacheable as long as the viewer keeps an act (viewer.acts.cache-for): a
 * timeline changes only when Cellar publishes a consolidation. An ELI no act has is 404.
 */
@RestController
class ActController {
  static final LinkRelation VERSIONS = LinkRelation.of("versions");

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

  @GetMapping(
      path = {EliPaths.ACT, EliPaths.TREATY},
      produces = MediaTypes.HAL_JSON_VALUE)
  ResponseEntity<RepresentationModel<?>> act(@RequestAttribute(EliPaths.ELI) Eli eli) {
    var act =
        acts.find(eli)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "No act has the ELI " + eli));
    var model =
        HalModelBuilder.halModelOf(ActRepresentation.of(act))
            .link(ActLinks.act(eli).withSelfRel())
            .link(eurLex(act))
            .link(cellar(act))
            .embed(act.timeline().versions(), VERSIONS)
            .build();
    return ResponseEntity.ok().cacheControl(cacheControl).body(model);
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
