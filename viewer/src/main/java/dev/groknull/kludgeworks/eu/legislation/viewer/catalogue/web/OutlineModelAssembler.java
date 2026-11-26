// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiTextController.OutlineModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.TextModelAssembler.Asked;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representation of the outline of an act's legal text in the API. */
@Component
class OutlineModelAssembler {

  /**
   * Returns the representation of the outline of an act's version in a language: links to {@code
   * self}, {@code up} (the text), and {@code av:occurrence} (template).
   */
  public EntityModel<OutlineModel> toModel(Asked asked, Outline outline) {
    var celex = asked.act().id();
    var key = ApiActResources.key(asked.version());
    var languageCode = ApiActResources.code(asked.language().code());
    var text =
        TextModelAssembler.to(celex, asked.version(), asked.language(), IanaLinkRelations.UP);
    return EntityModel.of(
        OutlineModel.of(outline),
        linkTo(methodOn(ApiTextController.class).outline(celex, key, languageCode)).withSelfRel(),
        text,
        TextModelAssembler.occurrenceTemplate(text));
  }
}
