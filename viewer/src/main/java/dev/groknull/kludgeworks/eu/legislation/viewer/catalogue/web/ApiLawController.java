// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.code;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.DeferredApplication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LawOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.DateModel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The law of an act on a day, in the API. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiLawController {
  private final Catalogued catalogued;
  private final Texts texts;

  ApiLawController(Catalogued catalogued, Texts texts) {
    this.catalogued = catalogued;
    this.texts = texts;
  }

  /**
   * Returns the law on {@code day}: the day asked, the versions that apply on it, the day they
   * apply until, the deferred application of the act, and where their text is not the law on the
   * day, because of a pending or a prospective amendment. More than one version applies when the
   * original text and a consolidation share a day, in different languages.
   *
   * <p>A day before the first version returns 404, as a problem of the type {@link
   * ProblemType#BEFORE_FIRST_VERSION}. It has the day the act was adopted, the day its text first
   * applies, and the address of its first version. The day asked is never moved.
   */
  @GetMapping(ACT + "/on/{day}")
  RepresentationModel<?> on(
      @PathVariable ActIdentifier celex,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
    var act = catalogued.api(celex);
    var timeline = act.timeline();
    var applying = timeline.allApplicableOn(day);
    if (applying.isEmpty()) {
      throw beforeTheFirstVersion(act, day);
    }
    return HalModelBuilder.halModelOf(
            new LawModel(
                day,
                timeline.nextStartAfter(day).orElse(null),
                deferredApplication(act, applying, day),
                divergences(act, applying, day)))
        .embed(
            applying.stream().map(version -> ApiActResources.summary(celex, version)).toList(),
            Relation.VERSION.rel())
        .link(linkTo(methodOn(ApiLawController.class).on(celex, day)).withSelfRel())
        .link(ApiActResources.up(celex))
        .build();
  }

  /**
   * Returns the deferred application of {@code act} on {@code date}, with the languages of the
   * versions {@code applying} in which the reader gets a text on that day. Null if the act applies
   * in full, or is not in force.
   */
  private static @Nullable DeferredApplicationModel deferredApplication(
      Act act, List<Version> applying, LocalDate date) {
    var languages = new LinkedHashMap<DeferredApplication, List<String>>();
    applying.stream()
        .flatMap(version -> version.languages().stream())
        .distinct()
        .sorted()
        .forEach(language ->
            LawOnADay.of(act)
                .deferredApplicationOn(date, language)
                .ifPresent(each ->
                    languages
                        .computeIfAbsent(each, key -> new ArrayList<>())
                        .add(code(language.code()))));
    return languages.entrySet().stream()
        .findFirst()
        .map(each ->
            new DeferredApplicationModel(
                DateModel.of(each.getKey().inFull()), each.getKey().partial(), each.getValue()))
        .orElse(null);
  }

  /**
   * Returns the divergences of {@code act} on {@code date}, each with the languages of the versions
   * {@code applying} whose text differs. An amending act can change the text of some languages
   * only. The tags of the texts that are built in a language tell where its text differs. A
   * language without built texts is compared act by act.
   */
  private List<DivergenceModel> divergences(Act act, List<Version> applying, LocalDate date) {
    var languages = new LinkedHashMap<Divergence, List<String>>();
    applying.stream()
        .flatMap(version -> version.languages().stream())
        .distinct()
        .sorted()
        .forEach(language ->
            LawOnADay.of(act)
                .divergences(date, language, texts.tags(texts.preparation(act, language)))
                .forEach(each ->
                    languages
                        .computeIfAbsent(each, key -> new ArrayList<>())
                        .add(code(language.code()))));
    return languages.entrySet().stream()
        .map(each -> {
          var divergence = each.getKey();
          var included = divergence.included();
          return new DivergenceModel(
              code(divergence.kind().name()),
              divergence.act().celex(),
              act.cited(divergence.act()),
              divergence.kind() == Divergence.Kind.UNDATED ? null : divergence.appliesFrom(),
              included == null ? null : ApiActResources.key(included),
              divergence.places().stream()
                  .map(place -> new PlaceModel(place.location().code(), place.place().words()))
                  .distinct()
                  .toList(),
              each.getValue());
        })
        .toList();
  }

  private static ErrorResponseException beforeTheFirstVersion(Act act, LocalDate date) {
    var first = act.timeline().first();
    var problem =
        ProblemType.BEFORE_FIRST_VERSION.problem(
            "The text of " + act.id() + " first applies on " + first.date() + ".");
    problem.setProperty("day", date);
    problem.setProperty("dateOfDocument", act.dateOfDocument());
    problem.setProperty("firstApplies", first.date());
    problem.setProperty(
        "firstVersion", ApiActResources.to(act.id(), first, IanaLinkRelations.FIRST).getHref());
    return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
  }

  /**
   * The law on a day, as the API shows it.
   *
   * @param day the day asked
   * @param until the day the next version starts, or null if no version starts later
   * @param deferredApplication the deferred application of the act itself on the day, or null if it
   *     applies in full
   * @param divergences where the text of the versions is not the law on the day: its pending and
   *     prospective amendments
   */
  record LawModel(
      LocalDate day,
      @Nullable LocalDate until,
      @Nullable DeferredApplicationModel deferredApplication,
      List<DivergenceModel> divergences) {}

  /**
   * The deferred application of the act on the day, as the API shows it: the act is in force, but
   * it does not apply in full yet. For example, 2016/679 on 1 March 2017.
   *
   * @param inFull the date from which the act applies in full, with the clause that sets it
   * @param partial whether a part of the act applies on the day already
   * @param languages the languages in which the reader gets a text on the day
   */
  record DeferredApplicationModel(DateModel inFull, boolean partial, List<String> languages) {}

  /**
   * Where the text is not the law on a day, as the API shows it.
   *
   * @param kind {@code pending}: a pending amendment, the act applies, but the text does not
   *     include it. {@code prospective}: a prospective amendment, the text includes it, but the act
   *     does not apply yet. {@code undated}: an undated pending amendment, the act is in force, the
   *     text does not include it, and Cellar does not give the day from which it applies.
   * @param act the CELEX number of the act
   * @param cited how the Official Journal cites it. For example, {@code 2017/815}.
   * @param appliesFrom the day from which the act applies. Null if Cellar does not give it.
   * @param includedIn the first version that includes its text: {@code oj}, or its date. Null for a
   *     pending amendment that no version includes yet.
   * @param places the provisions where the text differs. Empty where the whole amending act
   *     differs.
   * @param languages the languages whose text differs
   */
  record DivergenceModel(
      String kind,
      String act,
      String cited,
      @Nullable LocalDate appliesFrom,
      @Nullable String includedIn,
      List<PlaceModel> places,
      List<String> languages) {}

  /**
   * A provision where the text is not the law, as the API shows it.
   *
   * @param location where, in Cellar's codes, as in {@code amendments}. For example, {@code AN PO
   *     4.1.3.3}.
   * @param name the provision, as a page cites it. For example, {@code point 4.1.3.3}.
   */
  record PlaceModel(String location, String name) {}
}
