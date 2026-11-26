// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheLaw;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheLaw.Deferred;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheLaw.Diverged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.DateModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiLawController.DeferredApplicationModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiLawController.DivergenceModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiLawController.LawModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiLawController.PlaceModel;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Assembles the hypermedia representation of the law of an act on a day in the API. */
@Component
class LawModelAssembler implements RepresentationModelAssembler<TheLaw, RepresentationModel<?>> {

  /**
   * Returns the representation of the law of an act on a day: the day asked, the day until which
   * the versions apply, the deferred application of the act itself, and its divergences in each
   * language, with the applying versions embedded, {@code self} and {@code up}.
   */
  @Override
  public RepresentationModel<?> toModel(TheLaw law) {
    var act = law.act();
    var celex = act.id();
    var day = law.day();
    var deferred = deferredModel(law.deferred());
    var divergences = divergenceModels(act, law.divergences());
    return HalModelBuilder.halModelOf(new LawModel(day, law.until(), deferred, divergences))
        .embed(
            law.applying().stream()
                .map(version -> ApiActResources.summary(celex, version))
                .toList(),
            Relation.VERSION.rel())
        .link(linkTo(methodOn(ApiLawController.class).on(celex, day)).withSelfRel())
        .link(ApiActResources.up(celex))
        .build();
  }

  private static @Nullable DeferredApplicationModel deferredModel(@Nullable Deferred deferred) {
    if (deferred == null) {
      return null;
    }
    var app = deferred.application();
    var languages =
        deferred.languages().stream().map(lang -> ApiActResources.code(lang.code())).toList();
    return new DeferredApplicationModel(DateModel.of(app.inFull()), app.partial(), languages);
  }

  private static List<DivergenceModel> divergenceModels(Act act, List<Diverged> divergences) {
    return divergences.stream()
        .map(each -> {
          var divergence = each.divergence();
          var included = divergence.included();
          var languages =
              each.languages().stream().map(lang -> ApiActResources.code(lang.code())).toList();
          return new DivergenceModel(
              ApiActResources.code(divergence.kind().name()),
              divergence.act().celex(),
              act.cited(divergence.act()),
              divergence.applicationDate().orElse(null),
              included == null ? null : ApiActResources.key(included),
              divergence.places().stream()
                  .map(place -> new PlaceModel(place.location().code(), place.place().words()))
                  .distinct()
                  .toList(),
              languages);
        })
        .toList();
  }
}
