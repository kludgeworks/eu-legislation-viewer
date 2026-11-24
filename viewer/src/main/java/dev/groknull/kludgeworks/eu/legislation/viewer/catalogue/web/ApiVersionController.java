// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.VERSION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.VERSIONS;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.VersionModel;
import java.time.Clock;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The versions of an act in the API, and each version. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiVersionController {
  /**
   * RFC 5829's relation of the version after a version. Spring HATEOAS lists it under another name.
   */
  private static final LinkRelation SUCCESSOR_VERSION = LinkRelation.of("successor-version");

  private final Catalogued catalogued;
  private final Clock clock;

  ApiVersionController(Catalogued catalogued, Clock clock) {
    this.catalogued = catalogued;
    this.clock = clock;
  }

  /**
   * Returns the versions of the act, in date order:
   *
   * <ul>
   *   <li>{@code item}: each version, embedded.
   *   <li>{@code first}: the original text. The versions are a series, and they are not paged.
   *   <li>{@code latest-version}: as on the act.
   * </ul>
   */
  @GetMapping(VERSIONS)
  RepresentationModel<?> versions(@PathVariable ActIdentifier celex) {
    var act = catalogued.api(celex);
    var model =
        HalModelBuilder.halModel()
            .embed(
                act.timeline().versions().stream()
                    .map(version -> ApiActResources.summary(celex, version))
                    .toList(),
                IanaLinkRelations.ITEM)
            .link(linkTo(methodOn(ApiVersionController.class).versions(celex)).withSelfRel())
            .link(ApiActResources.up(celex))
            .link(ApiActResources.to(celex, act.timeline().first(), IanaLinkRelations.FIRST));
    ApiActResources.latest(act, clock).ifPresent(model::link);
    return model.build();
  }

  /**
   * Returns the version at {@code key}: {@code oj} for the original text, or the date of a
   * consolidation. Returns 404 for any other key. It links to the versions before and after it
   * among all the versions of the act, in any language. It links to its text in each of its
   * languages, {@code av:text}, with the language in {@code hreflang}.
   */
  @GetMapping(VERSION)
  EntityModel<VersionModel> version(@PathVariable ActIdentifier celex, @PathVariable String key) {
    var versions = catalogued.api(celex).timeline().versions();
    var version = ApiActResources.at(versions, key);
    var at = versions.indexOf(version);
    var model =
        ApiActResources.summary(celex, version)
            .add(ApiActResources.up(celex))
            .add(ApiActResources.versionHistory(celex));
    if (at > 0) {
      model.add(
          ApiActResources.to(celex, versions.get(at - 1), IanaLinkRelations.PREDECESSOR_VERSION));
    }
    if (at < versions.size() - 1) {
      model.add(ApiActResources.to(celex, versions.get(at + 1), SUCCESSOR_VERSION));
    }
    for (var language : version.languages()) {
      model.add(ApiTextController.to(celex, version, language, Relation.TEXT.rel()));
    }
    return model;
  }
}
