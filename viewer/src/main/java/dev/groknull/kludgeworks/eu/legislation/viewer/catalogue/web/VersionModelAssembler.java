// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Today;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.VersionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representations of {@link Version}s in the API. */
@Component
class VersionModelAssembler
    implements RepresentationModelAssembler<
        VersionModelAssembler.ActVersion, EntityModel<VersionModel>> {
  /**
   * RFC 5829's relation of the version after a version. Spring HATEOAS lists it under another name.
   */
  static final LinkRelation SUCCESSOR_VERSION = LinkRelation.of("successor-version");

  /** A version of an act. */
  record ActVersion(Act act, Version version) {}

  private final Today today;

  VersionModelAssembler(Today today) {
    this.today = today;
  }

  /**
   * Returns the representation of a single version of an act: its summary, links to the act ({@code
   * up}), its versions ({@code version-history}), the versions before and after it ({@code
   * predecessor-version} and {@code successor-version}), and its text in each of its languages
   * ({@code av:text}).
   */
  @Override
  public EntityModel<VersionModel> toModel(ActVersion actVersion) {
    var act = actVersion.act();
    var version = actVersion.version();
    var celex = act.id();
    var versions = act.timeline().versions();
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
      model.add(TextModelAssembler.to(celex, version, language, Relation.TEXT.rel()));
    }
    return model;
  }

  /**
   * Returns the collection representation of all versions of an act, with each version embedded as
   * {@code item}, {@code self}, {@code up}, {@code first}, and {@code latest-version} if one
   * applies today.
   */
  public RepresentationModel<?> toCollectionModel(Act act) {
    var celex = act.id();
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
    ApiActResources.latest(act, today.today()).ifPresent(model::link);
    return model.build();
  }
}
