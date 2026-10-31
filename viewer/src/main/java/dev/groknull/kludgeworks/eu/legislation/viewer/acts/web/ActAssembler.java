// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.stereotype.Component;

/**
 * An act as HAL (URLs and API: public API): its identifiers and dates, its versions embedded as
 * summaries with their links (never the text), the versions resource (with {@code ?on} the one that
 * applies on a date), the first version and the latest (TimelineLinks), comparing two versions and
 * its amendments (both planned: 501), and its work in Cellar as an alternate. No EUR-Lex page:
 * EUR-Lex's are each in a language, which an act's resource isn't, so a text links its own (Links
 * back to EUR-Lex).
 */
@Component
class ActAssembler {
  static final HalLinkRelation COMPARE = ApiRelations.of("compare");
  static final HalLinkRelation AMENDMENTS = ApiRelations.of("amendments");

  private final URI cellarResources;

  /**
   * @param cellarResources where Cellar's resource URIs start (viewer.cellar.resources)
   */
  ActAssembler(@Value("${viewer.cellar.resources}") URI cellarResources) {
    this.cellarResources = cellarResources;
  }

  RepresentationModel<?> toModel(Act act) {
    var id = act.id();
    return HalModelBuilder.halModelOf(ActRepresentation.of(act))
        .link(ActLinks.act(id, IanaLinkRelations.SELF))
        .link(ActLinks.versions(id, ActLinks.VERSIONS))
        .links(TimelineLinks.ends(act.timeline(), ActLinks.toVersion(id)))
        .link(ActLinks.compare(id, COMPARE))
        .link(ActLinks.amendments(id, AMENDMENTS))
        .link(ActLinks.cellar(cellarResources, id.celex(), "The work in Cellar"))
        .embed(ActLinks.summaries(act), ActLinks.VERSIONS)
        .build();
  }
}
