// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiTextController.OccurrenceModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.TextModelAssembler.Asked;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.OccurrenceWithFootnotes;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.ApiLinks;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
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
   * address, {@code up} (the text), and {@code describedby} schema, together with URLs for any
   * images referenced in the occurrence or its footnotes.
   */
  public EntityModel<OccurrenceModel> toModel(
      Asked asked,
      TextId textId,
      String eId,
      OccurrenceWithFootnotes occurrence,
      Map<String, URI> sourceUris,
      String imageVersion) {
    var text =
        TextModelAssembler.to(
            asked.act().id(), asked.version(), asked.language(), IanaLinkRelations.UP);
    var relevant = new LinkedHashMap<String, URI>();
    if (!sourceUris.isEmpty()) {
      for (var file : occurrence.imageFiles()) {
        var uri = sourceUris.get(file);
        if (uri != null) {
          relevant.put(file, uri);
        }
      }
    }
    var model =
        new OccurrenceModel(
            occurrence.occurrence(),
            occurrence.footnotes(),
            TextModelAssembler.imageLinks(textId, relevant, imageVersion));
    return EntityModel.of(
        model,
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
