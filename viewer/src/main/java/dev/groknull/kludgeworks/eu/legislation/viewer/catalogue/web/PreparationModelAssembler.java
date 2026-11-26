// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiPreparationController.PreparationModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiPreparationController.Progress;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/**
 * Assembles the hypermedia representation of an act's preparation in a language in the API: the
 * progress of each version's text, the SSE change stream, and the retry link when failures can be
 * recovered.
 */
@Component
class PreparationModelAssembler
    implements RepresentationModelAssembler<Preparation, RepresentationModel<?>> {

  /**
   * Returns the representation of an act's preparation in a language, with the state of each
   * version's text embedded as {@code item}, {@code self}, {@code up}, {@code av:events}, and
   * {@code av:retry} if retryable.
   */
  @Override
  public RepresentationModel<?> toModel(Preparation preparation) {
    var celex = preparation.act();
    var language = ApiActResources.code(preparation.language().code());
    var self = ApiActResources.preparation(celex, preparation.language()).withSelfRel();
    var model =
        HalModelBuilder.halModelOf(PreparationModel.of(preparation))
            .embed(
                preparation.texts().entrySet().stream()
                    .map(each ->
                        progress(celex, preparation.language(), each.getKey(), each.getValue()))
                    .toList(),
                IanaLinkRelations.ITEM)
            .link(self)
            .link(ApiActResources.up(celex))
            .link(
                linkTo(methodOn(ApiPreparationController.class).events(celex, language))
                    .withRel(Relation.EVENTS.rel()));
    if (preparation.retryable()) {
      model.link(
          ApiActResources.preparation(celex, preparation.language()).withRel(Relation.RETRY.rel()));
    }
    return model.build();
  }

  private static EntityModel<Progress> progress(
      ActIdentifier celex, Language language, Version version, Text text) {
    var model = EntityModel.of(Progress.of(version, text));
    return switch (text) {
      case Text.Ready _ ->
          model.add(TextModelAssembler.to(celex, version, language, Relation.TEXT.rel()));
      case Text.Waiting _, Text.Converting _, Text.Failed _ -> model;
    };
  }
}
