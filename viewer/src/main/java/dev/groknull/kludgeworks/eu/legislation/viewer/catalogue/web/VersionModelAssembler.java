// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.VersionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representation of a single {@link Version} in the API. */
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
}
