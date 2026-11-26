// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.TextModelAssembler.Asked;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.OccurrenceWithFootnotes;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.ApiLinks;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/** Assembles the hypermedia representation of an occurrence of an act's legal text in the API. */
@Component
class OccurrenceModelAssembler {

  /**
   * Returns the representation of an occurrence of a text at {@code eId}: links to its canonical
   * address, {@code up} (the text), and {@code describedby} schema.
   */
  public EntityModel<OccurrenceWithFootnotes> toModel(
      Asked asked, String eId, OccurrenceWithFootnotes occurrence) {
    var text =
        TextModelAssembler.to(
            asked.act().id(), asked.version(), asked.language(), IanaLinkRelations.UP);
    return EntityModel.of(
        occurrence,
        Link.of(occurrenceHref(text, eId)),
        text,
        ApiLinks.schema("/api/schemas/v1/occurrence.schema.json"));
  }

  /** Returns the address of the occurrence at {@code eId} in a text. Its {@code /} stays. */
  private static String occurrenceHref(Link text, String eId) {
    return text.getHref()
        + "/occurrences/"
        + Arrays.stream(eId.split("/", -1))
            .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
            .collect(Collectors.joining("/"));
  }
}
