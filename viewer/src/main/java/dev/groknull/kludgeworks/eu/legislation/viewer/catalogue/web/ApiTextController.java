// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.TextModelAssembler.Asked;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The text of a version in a language, in the API, with its outline and its occurrences. The text
 * is in the public model ({@link PublicText}), not in the form that the content module stores.
 *
 * <ul>
 *   <li>A request for a text, its outline or an occurrence starts the preparation of the act in the
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
  private static final String TEXT = ApiActResources.VERSION + "/texts/{language}";

  /** How long a client waits before it asks again for a text that is not ready. */
  private static final Duration RETRY_AFTER = Duration.ofSeconds(5);

  private final Catalogued acts;
  private final Texts texts;
  private final Content content;
  private final Images images;
  private final TextModelAssembler assembler;
  private final OutlineModelAssembler outlineAssembler;
  private final OccurrenceModelAssembler occurrenceAssembler;
  private final PreparationModelAssembler preparationAssembler;

  ApiTextController(
      Catalogued acts,
      Texts texts,
      Content content,
      Images images,
      TextModelAssembler assembler,
      OutlineModelAssembler outlineAssembler,
      OccurrenceModelAssembler occurrenceAssembler,
      PreparationModelAssembler preparationAssembler) {
    this.acts = acts;
    this.texts = texts;
    this.content = content;
    this.images = images;
    this.assembler = assembler;
    this.outlineAssembler = outlineAssembler;
    this.occurrenceAssembler = occurrenceAssembler;
    this.preparationAssembler = preparationAssembler;
  }

  /**
   * Returns the text of the version at {@code key} in the language {@code language}, for example
   * {@code eng}:
   *
   * <ul>
   *   <li>{@code up}: the version.
   *   <li>{@code alternate}: the text of the same version in each other language.
   *   <li>{@code av:outline}: the outline of the text.
   *   <li>{@code av:occurrence}: an occurrence by its eId, a URI template.
   *   <li>{@code av:preparation}: how far the preparation of the act in the language is.
   *   <li>{@code describedby}: the JSON Schema of the text.
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
                .map(publicText ->
                    assembler.toModel(
                        asked, text, publicText, images.sourceUris(text), images.version())));
  }

  /**
   * Returns the outline of the text: its occurrences that have a title, nested by rank. It links to
   * the text, {@code up}, and to an occurrence by its eId, {@code av:occurrence}.
   */
  @GetMapping(TEXT + "/outline")
  ResponseEntity<RepresentationModel<?>> outline(
      @PathVariable ActIdentifier celex, @PathVariable String key, @PathVariable String language) {
    var asked = asked(celex, key, language);
    return answer(
        asked, id -> content.outline(id).map(outline -> outlineAssembler.toModel(asked, outline)));
  }

  /**
   * Returns the occurrence {@code eId} of the text, with everything in it. Its eId is the rest of
   * the path, for example {@code anx/pnt_1.2}. Returns 404 if the text has no such occurrence. It
   * links to the text, {@code up}, and to its JSON Schema, {@code describedby}.
   */
  @GetMapping(TEXT + "/occurrences/{*eId}")
  ResponseEntity<RepresentationModel<?>> occurrence(
      @PathVariable ActIdentifier celex,
      @PathVariable String key,
      @PathVariable String language,
      @PathVariable("eId") String path) {
    var eId = path.substring(1);
    var asked = asked(celex, key, language);
    return answer(
        asked,
        id ->
            content
                .publicOccurrence(id, eId)
                .map(occurrence ->
                    occurrenceAssembler.toModel(
                        asked, id, eId, occurrence, images.sourceUris(id), images.version())));
  }

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
              .body(preparationAssembler.toModel(preparation));
      case null -> throw Requests.notFound();
    };
  }

  /** Returns what the path asks for, or throws not found. */
  private Asked asked(ActIdentifier celex, String key, String code) {
    var act = acts.api(celex);
    var version = ApiActResources.at(act.timeline().versions(), key);
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
        "preparation", ApiActResources.preparation(asked.act().id(), asked.language()).getHref());
    return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
  }

  /**
   * Addresses of an image file in a stored text.
   *
   * @param preview address of the scaled WebP image served by the viewer
   * @param original address of the full-size WebP image served by the viewer
   * @param source address of the original TIFF item in Cellar, or null
   */
  record ImageLinks(
      String preview,
      String original,
      @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String source) {}

  /**
   * The text of a version in a language, as the API shows it.
   *
   * @param version where the version is among the versions: {@code oj}, or its date
   * @param celex the CELEX number of the document that holds the text
   * @param language the language, by the EU's code. For example, {@code eng}.
   * @param text the text, in the public model
   * @param images addresses of the image files of the text, keyed by file name
   */
  record TextModel(
      String version,
      String celex,
      String language,
      PublicText text,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, ImageLinks> images) {}

  /**
   * One occurrence of a text and its footnotes, as the API shows it.
   *
   * @param occurrence the occurrence
   * @param footnotes the footnotes that the occurrence refers to, in the order of the text
   * @param images addresses of the image files referenced in the occurrence, keyed by file name
   */
  record OccurrenceModel(
      PublicText.Occurrence occurrence,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<PublicText.Footnote> footnotes,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, ImageLinks> images) {}

  /**
   * The outline of a text, as the API shows it.
   *
   * @param entries the outermost occurrences that have a title
   */
  record OutlineModel(List<Entry> entries) {

    static OutlineModel of(Outline outline) {
      return new OutlineModel(outline.entries().stream().map(Entry::of).toList());
    }

    /**
     * An occurrence in the outline.
     *
     * @param eId its eId. For example, {@code art_3}.
     * @param kind its kind in legal-text. For example, {@code article}.
     * @param num its label as printed. For example, "Article 3". Empty if it has none.
     * @param heading its heading. Empty if it has none.
     * @param entries the occurrences ranked below it
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
