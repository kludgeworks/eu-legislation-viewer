// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * The text of a version in a language, in the API, with its outline and its units. The text is in
 * the public model ({@link PublicText}), not in the form that the content module stores.
 *
 * <ul>
 *   <li>A request for a text, its outline or a unit starts the preparation of the act in the
 *       language, as a page of the act does. The client does not ask for it: the request is safe in
 *       the sense of RFC 9110.
 *   <li>While the text is not ready, the answer is 202, with the preparation and {@code
 *       Retry-After}. There is no link to the text until it is ready.
 *   <li>A text that could not be built is a problem of the type {@link ProblemType#TEXT_FAILED}.
 *   <li>A text that is ready has the identity of the stored text as its entity tag. A text that is
 *       stored again gets a new identity. The tag is weak, because Tomcat does not compress a
 *       response with a strong one.
 * </ul>
 */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiTextController {
  private static final String TEXT = ApiActController.VERSION + "/texts/{language}";

  /**
   * The IANA relation of a link to the schema of an answer, as the registry spells it. Spring
   * HATEOAS's {@code IanaLinkRelations.DESCRIBED_BY} is {@code describedBy}, and a HAL client looks
   * up its links by the exact name.
   */
  private static final LinkRelation DESCRIBED_BY = LinkRelation.of("describedby");

  /** How long a client waits before it asks again for a text that is not ready. */
  private static final Duration RETRY_AFTER = Duration.ofSeconds(5);

  private final Catalogued acts;
  private final Texts texts;
  private final Content content;

  ApiTextController(Catalogued acts, Texts texts, Content content) {
    this.acts = acts;
    this.texts = texts;
    this.content = content;
  }

  /**
   * Returns the text of the version at {@code key} in the language {@code language}, for example
   * {@code eng}:
   *
   * <ul>
   *   <li>{@code up}: the version.
   *   <li>{@code alternate}: the text in each other language of the version, with {@code hreflang}.
   *   <li>{@code av:outline}, {@code av:unit}: its outline, and a unit by its eId.
   *   <li>{@code av:preparation}: the preparation of the act in the language.
   *   <li>{@code describedby}: the JSON Schema of the answer.
   * </ul>
   */
  @GetMapping(TEXT)
  ResponseEntity<RepresentationModel<?>> text(
      @PathVariable ActIdentifier celex, @PathVariable String key, @PathVariable String language) {
    var asked = asked(celex, key, language);
    return answer(
        asked,
        text ->
            content
                .publicText(text)
                .map(
                    publicText -> {
                      var self =
                          to(celex, asked.version(), asked.language(), IanaLinkRelations.SELF);
                      var model =
                          HalModelBuilder.halModelOf(
                                  new TextModel(
                                      ApiActController.key(asked.version()),
                                      asked.version().celex(),
                                      ApiActController.code(asked.language().code()),
                                      publicText))
                              .link(self)
                              .link(
                                  ApiActController.to(celex, asked.version(), IanaLinkRelations.UP))
                              .link(
                                  linkTo(
                                          methodOn(ApiTextController.class)
                                              .outline(celex, key, language))
                                      .withRel(Relation.OUTLINE.rel()))
                              .link(unitTemplate(self))
                              .link(schema("text"))
                              .link(ApiPreparationController.to(celex, asked.language()));
                      for (var other : asked.version().languages()) {
                        if (other != asked.language()) {
                          model.link(
                              to(celex, asked.version(), other, IanaLinkRelations.ALTERNATE));
                        }
                      }
                      return model.build();
                    }));
  }

  /**
   * Returns the outline of the text: its units that have a title, nested by rank. It links to the
   * text, {@code up}, and to a unit by its eId, {@code av:unit}.
   */
  @GetMapping(TEXT + "/outline")
  ResponseEntity<RepresentationModel<?>> outline(
      @PathVariable ActIdentifier celex, @PathVariable String key, @PathVariable String language) {
    var asked = asked(celex, key, language);
    var text = to(celex, asked.version(), asked.language(), IanaLinkRelations.UP);
    return answer(
        asked,
        id ->
            content
                .outline(id)
                .map(
                    outline ->
                        HalModelBuilder.halModelOf(OutlineModel.of(outline))
                            .link(
                                linkTo(
                                        methodOn(ApiTextController.class)
                                            .outline(celex, key, language))
                                    .withSelfRel())
                            .link(text)
                            .link(unitTemplate(text))
                            .build()));
  }

  /**
   * Returns the unit {@code eId} of the text, with everything in it. Its eId is the rest of the
   * path, for example {@code anx/pnt_1.2}. Returns 404 if the text has no such unit. It links to
   * the text, {@code up}, and to its JSON Schema, {@code describedby}.
   */
  @GetMapping(TEXT + "/units/{*eId}")
  ResponseEntity<RepresentationModel<?>> unit(
      @PathVariable ActIdentifier celex,
      @PathVariable String key,
      @PathVariable String language,
      @PathVariable("eId") String path) {
    var eId = path.substring(1);
    var asked = asked(celex, key, language);
    var text = to(celex, asked.version(), asked.language(), IanaLinkRelations.UP);
    return answer(
        asked,
        id ->
            content
                .publicUnit(id, eId)
                .map(
                    extract ->
                        HalModelBuilder.halModelOf(new UnitModel(extract.unit(), extract.notes()))
                            .link(Link.of(unitHref(text, eId)))
                            .link(text)
                            .link(schema("unit"))
                            .build()));
  }

  /** Returns a link to the text of the version in the language. */
  static Link to(ActIdentifier celex, Version version, Language language, LinkRelation relation) {
    return linkTo(
            methodOn(ApiTextController.class)
                .text(celex, ApiActController.key(version), ApiActController.code(language.code())))
        .withRel(relation)
        .withHreflang(language.iso());
  }

  /**
   * Answers with the text that {@code ready} makes of the stored text, once it is ready. Answers
   * 202 with the preparation while it is not, and a problem if it failed.
   */
  private ResponseEntity<RepresentationModel<?>> answer(
      Asked asked, Function<TextId, Optional<RepresentationModel<?>>> ready) {
    var preparation = texts.prepare(asked.act(), asked.language());
    return switch (preparation.texts().get(asked.version())) {
      case Text.Ready(var id) ->
          ResponseEntity.ok()
              .eTag("W/\"" + id + "\"")
              .header(HttpHeaders.CONTENT_LANGUAGE, asked.language().iso())
              .body(ready.apply(id).orElseThrow(Requests::notFound));
      case Text.Failed failed -> throw failed(asked, failed);
      case Text.Waiting _, Text.Converting _ ->
          ResponseEntity.status(HttpStatus.ACCEPTED)
              .header(HttpHeaders.RETRY_AFTER, Long.toString(RETRY_AFTER.toSeconds()))
              .body(ApiPreparationController.model(asked.act(), preparation));
      case null -> throw Requests.notFound();
    };
  }

  /** Returns what the path asks for, or throws not found. */
  private Asked asked(ActIdentifier celex, String key, String code) {
    var act = acts.api(celex);
    var version = ApiActController.at(act.timeline().versions(), key);
    var language = Requests.language(code);
    if (!version.languages().contains(language)) {
      throw Requests.notFound();
    }
    return new Asked(act, version, language);
  }

  private static ErrorResponseException failed(Asked asked, Text.Failed failed) {
    var problem =
        ProblemType.TEXT_FAILED.problem(
            "The text of "
                + asked.version().celex()
                + " in "
                + asked.language().english()
                + " could not be built.");
    problem.setProperty("failure", ApiPreparationController.code(failed.failure()));
    problem.setProperty("retryable", failed.failure().retryable());
    problem.setProperty(
        "preparation", ApiPreparationController.to(asked.act().id(), asked.language()).getHref());
    return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
  }

  /**
   * Returns the link to the JSON Schema of an answer, {@code describedby}. The schemas are static
   * files, one folder for each major version of the API.
   */
  static Link schema(String answer) {
    return Link.of(
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/schemas/v1/{answer}.schema.json")
            .buildAndExpand(answer)
            .toUriString(),
        DESCRIBED_BY);
  }

  /** Returns the link to a unit of {@code text} by its eId, a URI template. */
  private static Link unitTemplate(Link text) {
    return Link.of(UriTemplate.of(text.getHref() + "/units/{+eId}"), Relation.UNIT.rel());
  }

  /** Returns the address of the unit {@code eId} of {@code text}. Its {@code /} stays. */
  private static String unitHref(Link text, String eId) {
    return text.getHref()
        + "/units/"
        + Arrays.stream(eId.split("/", -1))
            .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
            .collect(Collectors.joining("/"));
  }

  /** The version and language that a path names, of its act. */
  private record Asked(Act act, Version version, Language language) {}

  /**
   * The text of a version in a language, as the API shows it.
   *
   * @param version where the version is among the versions: {@code oj}, or its date
   * @param celex the CELEX number of the document that holds the text
   * @param language the language, by the EU's code. For example, {@code eng}.
   * @param text the text, in the public model
   */
  record TextModel(String version, String celex, String language, PublicText text) {}

  /**
   * A unit of a text, as the API shows it.
   *
   * @param unit the unit, in the public model
   * @param notes the notes that the unit refers to, in the order of the text
   */
  record UnitModel(
      PublicText.Unit unit,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<PublicText.Note> notes) {}

  /**
   * The outline of a text, as the API shows it.
   *
   * @param entries the outermost units that have a title
   */
  record OutlineModel(List<Entry> entries) {

    static OutlineModel of(Outline outline) {
      return new OutlineModel(outline.entries().stream().map(Entry::of).toList());
    }

    /**
     * A unit in the outline.
     *
     * @param eId its eId. For example, {@code art_3}.
     * @param kind its kind in legal-text. For example, {@code article}.
     * @param num its label as printed. For example, "Article 3". Empty if it has none.
     * @param heading its heading. Empty if it has none.
     * @param entries the units ranked below it
     */
    record Entry(String eId, String kind, String num, String heading, List<Entry> entries) {

      static Entry of(Outline.Entry entry) {
        return new Entry(
            entry.eId(),
            entry.kind(),
            entry.num(),
            entry.heading(),
            entry.entries().stream().map(Entry::of).toList());
      }
    }
  }
}
