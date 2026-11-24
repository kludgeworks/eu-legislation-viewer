// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.code;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.DateModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What other acts did to the provisions of an act, in the API. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiAmendmentsController {
  private final Catalogued catalogued;

  ApiAmendmentsController(Catalogued catalogued) {
    this.catalogued = catalogued;
  }

  /**
   * Returns what other acts did to the provisions of the act, as Cellar annotates them: each
   * amendment with its amending act, where it acts, what it did, and Cellar's day for it. Cellar's
   * day can be stale where a later act moved a date of the amending act. Thus the representation
   * also has the own dates of each amending act, which Cellar keeps up to date.
   */
  @GetMapping(ACT + "/amendments")
  RepresentationModel<?> amendments(@PathVariable ActIdentifier celex) {
    var act = catalogued.api(celex);
    var dates = new TreeMap<String, List<DateModel>>();
    act.dates()
        .amending()
        .forEach(
            (amending, own) ->
                dates.put(amending.celex(), own.stream().map(DateModel::of).toList()));
    return HalModelBuilder.halModelOf(
            new AmendmentsModel(
                act.amendments().stream().map(each -> AmendmentModel.of(act, each)).toList(),
                dates))
        .link(linkTo(methodOn(ApiAmendmentsController.class).amendments(celex)).withSelfRel())
        .link(ApiActResources.up(celex))
        .build();
  }

  /**
   * What other acts did to the provisions of an act, as the API shows it.
   *
   * @param amendments each amendment, as Cellar annotates it
   * @param dates the own dates of each amending act, by its CELEX number
   */
  record AmendmentsModel(List<AmendmentModel> amendments, Map<String, List<DateModel>> dates) {}

  /**
   * An amendment of a provision, as the API shows it.
   *
   * @param act the CELEX number of the amending act
   * @param cited how the Official Journal cites the amending act. For example, {@code 2019/103}.
   * @param location where it acts, as Cellar codes it. For example, {@code AN PO 8.1.2.3 PTA (c)}.
   * @param role what it did: replacement, addition, deletion, repeal, amendment, extension,
   *     suspension, deferral or other
   * @param from the day it applies from, as Cellar has it, or null where Cellar has none
   * @param until the day it ends, for an amendment that lapses, or null for the others
   * @param languages the only languages it changes, by the EU's codes, for an act that corrects
   *     only some language versions. Empty if it changes all of them.
   */
  record AmendmentModel(
      String act,
      String cited,
      String location,
      String role,
      @Nullable LocalDate from,
      @Nullable LocalDate until,
      List<String> languages) {

    static AmendmentModel of(Act amended, Amendment amendment) {
      return new AmendmentModel(
          amendment.act().celex(),
          amended.cited(amendment.act()),
          amendment.location().code(),
          code(amendment.role().name()),
          amendment.from(),
          amendment.until(),
          amendment.languages().stream().map(language -> code(language.code())).sorted().toList());
    }
  }
}
