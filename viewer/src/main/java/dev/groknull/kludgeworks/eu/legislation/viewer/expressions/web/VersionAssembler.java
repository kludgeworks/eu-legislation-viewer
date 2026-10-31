// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.ActLinks;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.ApiRelations;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.TimelineLinks;
import java.util.ArrayList;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.stereotype.Component;

/**
 * An act's versions as HAL (URLs and API: public API). The list: each embedded as a summary with
 * its link, the first and the latest, up to the act. One version, language-neutral: links to its
 * text in each language it is in (Version.languages, by language tag), to the act
 * (version-history), the versions either side, the first and the latest (TimelineLinks), and what
 * changed since the one before (av:changes, planned: 501).
 */
@Component
class VersionAssembler {
  static final HalLinkRelation TEXT = ApiRelations.of("text");
  static final HalLinkRelation CHANGES = ApiRelations.of("changes");

  /** The act's versions. */
  RepresentationModel<?> toCollectionModel(Act act) {
    var id = act.id();
    return HalModelBuilder.emptyHalModel()
        .link(ActLinks.versions(id, IanaLinkRelations.SELF).expand())
        .link(ActLinks.act(id, IanaLinkRelations.UP))
        .links(TimelineLinks.ends(act.timeline(), ActLinks.toVersion(id)))
        .embed(ActLinks.summaries(act), ActLinks.VERSIONS)
        .build();
  }

  /** {@code version} of {@code act}. */
  RepresentationModel<?> toModel(Act act, Version version) {
    var id = act.id();
    var date = version.date();
    var links = new ArrayList<Link>();
    links.add(ActLinks.version(id, date, IanaLinkRelations.SELF));
    links.add(ActLinks.act(id, IanaLinkRelations.VERSION_HISTORY));
    links.addAll(TimelineLinks.around(act.timeline(), version, ActLinks.toVersion(id)));
    act.timeline()
        .predecessor(version)
        .ifPresent(v -> links.add(ActLinks.comparison(id, v.date(), date, CHANGES)));
    for (var language : version.languages()) {
      links.add(ActLinks.text(id, date, language, TEXT).withHreflang(language.iso()));
    }
    return HalModelBuilder.halModelOf(VersionRepresentation.of(version)).links(links).build();
  }
}
