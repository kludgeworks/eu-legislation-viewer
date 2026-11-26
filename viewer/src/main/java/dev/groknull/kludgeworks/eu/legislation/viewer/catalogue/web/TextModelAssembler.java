// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiTextController.TextModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.ApiLinks;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representation of an act's legal text in the API. */
@Component
class TextModelAssembler {

  /** What an address of a text in the API asks for: its act, its version and its language. */
  record Asked(Act act, Version version, Language language) {}

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
  public EntityModel<TextModel> toModel(Asked asked, PublicText text) {
    var celex = asked.act().id();
    var key = ApiActResources.key(asked.version());
    var languageCode = ApiActResources.code(asked.language().code());
    var self = to(celex, asked.version(), asked.language(), IanaLinkRelations.SELF);
    var model =
        EntityModel.of(
            new TextModel(key, asked.version().celex(), languageCode, text),
            self,
            ApiActResources.to(celex, asked.version(), IanaLinkRelations.UP),
            linkTo(methodOn(ApiTextController.class).outline(celex, key, languageCode))
                .withRel(Relation.OUTLINE.rel()),
            occurrenceTemplate(self),
            ApiLinks.schema("/api/schemas/v1/text.schema.json"),
            ApiActResources.preparation(celex, asked.language()));
    for (var other : asked.version().languages()) {
      if (other != asked.language()) {
        model.add(to(celex, asked.version(), other, IanaLinkRelations.ALTERNATE));
      }
    }
    return model;
  }

  static Link occurrenceTemplate(Link text) {
    return Link.of(
        UriTemplate.of(text.getHref() + "/occurrences/{+eId}"), Relation.OCCURRENCE.rel());
  }
}
