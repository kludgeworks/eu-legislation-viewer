// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiTextController.OutlineModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiTextController.TextModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.OccurrenceWithFootnotes;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.ApiLinks;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/** Assembles the hypermedia representations of an act's legal text in the API. */
@Component
class TextModelAssembler
    implements RepresentationModelAssembler<TextModelAssembler.TextAnswer, RepresentationModel<?>> {

  /** What an address of a text in the API asks for: its act, its version and its language. */
  record Asked(Act act, Version version, Language language) {}

  /** The text of a version in a language, as an answer to assemble. */
  record TextAnswer(Asked asked, PublicText text) {}

  /** The outline of a version in a language, as an answer to assemble. */
  record OutlineAnswer(Asked asked, Outline outline) {}

  /** An occurrence of a text, as an answer to assemble. */
  record OccurrenceAnswer(Asked asked, OccurrenceWithFootnotes occurrence, String eId) {}

  /** Returns a link to the text of the version in the language. */
  static Link to(ActIdentifier celex, Version version, Language language, LinkRelation relation) {
    return linkTo(
            methodOn(ApiTextController.class)
                .text(celex, ApiActResources.key(version), ApiActResources.code(language.code())))
        .withRel(relation)
        .withHreflang(language.iso());
  }

  /**
   * Returns the representation of the text of an act's version in a language: links to {@code
   * self}, {@code up} (the version), {@code av:outline}, {@code av:occurrence} (template), {@code
   * describedby} schema, {@code av:preparation}, and {@code alternate} for each other language.
   */
  @Override
  public RepresentationModel<?> toModel(TextAnswer answer) {
    var asked = answer.asked();
    var celex = asked.act().id();
    var key = ApiActResources.key(asked.version());
    var languageCode = ApiActResources.code(asked.language().code());
    var self = to(celex, asked.version(), asked.language(), IanaLinkRelations.SELF);
    var model =
        HalModelBuilder.halModelOf(
                new TextModel(key, asked.version().celex(), languageCode, answer.text()))
            .link(self)
            .link(ApiActResources.to(celex, asked.version(), IanaLinkRelations.UP))
            .link(
                linkTo(methodOn(ApiTextController.class).outline(celex, key, languageCode))
                    .withRel(Relation.OUTLINE.rel()))
            .link(occurrenceTemplate(self))
            .link(ApiLinks.schema("/api/schemas/v1/text.schema.json"))
            .link(ApiActResources.preparation(celex, asked.language()));
    for (var other : asked.version().languages()) {
      if (other != asked.language()) {
        model.link(to(celex, asked.version(), other, IanaLinkRelations.ALTERNATE));
      }
    }
    return model.build();
  }

  /**
   * Returns the representation of the outline of an act's version in a language: links to {@code
   * self}, {@code up} (the text), and {@code av:occurrence} (template).
   */
  public RepresentationModel<?> toOutlineModel(OutlineAnswer answer) {
    var asked = answer.asked();
    var celex = asked.act().id();
    var key = ApiActResources.key(asked.version());
    var languageCode = ApiActResources.code(asked.language().code());
    var text = to(celex, asked.version(), asked.language(), IanaLinkRelations.UP);
    return HalModelBuilder.halModelOf(OutlineModel.of(answer.outline()))
        .link(
            linkTo(methodOn(ApiTextController.class).outline(celex, key, languageCode))
                .withSelfRel())
        .link(text)
        .link(occurrenceTemplate(text))
        .build();
  }

  /**
   * Returns the representation of an occurrence of a text at {@code eId}: links to its canonical
   * address, {@code up} (the text), and {@code describedby} schema.
   */
  public RepresentationModel<?> toOccurrenceModel(OccurrenceAnswer answer) {
    var asked = answer.asked();
    var text = to(asked.act().id(), asked.version(), asked.language(), IanaLinkRelations.UP);
    return HalModelBuilder.halModelOf(answer.occurrence())
        .link(Link.of(occurrenceHref(text, answer.eId())))
        .link(text)
        .link(ApiLinks.schema("/api/schemas/v1/occurrence.schema.json"))
        .build();
  }

  private static Link occurrenceTemplate(Link text) {
    return Link.of(
        UriTemplate.of(text.getHref() + "/occurrences/{+eId}"), Relation.OCCURRENCE.rel());
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
