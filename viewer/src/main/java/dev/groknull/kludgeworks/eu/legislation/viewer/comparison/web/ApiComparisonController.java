// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Ends;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The comparisons of an act in the API: how its text in a language differs between two dates, and
 * each difference. A comparison is under its act, which links to its lookup ({@link
 * ActComparisonLinks}).
 *
 * <ul>
 *   <li>The lookup takes any two dates and redirects, with 303, to the canonical comparison: the
 *       first days of the versions that apply on them in the language.
 *   <li>A comparison is of the texts of two versions. While a text is not ready, the answer is 202,
 *       with {@code Retry-After}. A request starts the preparation of the act in the language, as
 *       the compare page does.
 *   <li>With an amending act, the comparison is of the version that integrates the act with the
 *       version before it: the wording just before the act's changes against the wording just
 *       after.
 * </ul>
 */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiComparisonController {
  private static final Logger LOG = LoggerFactory.getLogger(ApiComparisonController.class);

  /** The path of the comparisons of an act, under the act. */
  private static final String COMPARISONS = "/api/acts/{celex}/comparisons";

  private static final String COMPARISON = COMPARISONS + "/{from}..{to}/texts/{language}";

  /** The relation of a difference, which the viewer's CURIE documents. */
  static final HalLinkRelation DIFFERENCE = HalLinkRelation.curied("av", "difference");

  /** How long a client waits before it asks again for a comparison whose texts are not ready. */
  private static final Duration RETRY_AFTER = Duration.ofSeconds(5);

  private final Acts acts;
  private final Texts texts;
  private final Comparisons comparisons;
  private final Content content;
  private final ComparisonModelAssembler assembler;
  private final ComparisonPreparingModelAssembler preparingAssembler;
  private final DifferenceModelAssembler differenceAssembler;

  ApiComparisonController(
      Acts acts,
      Texts texts,
      Comparisons comparisons,
      Content content,
      ComparisonModelAssembler assembler,
      ComparisonPreparingModelAssembler preparingAssembler,
      DifferenceModelAssembler differenceAssembler) {
    this.acts = acts;
    this.texts = texts;
    this.comparisons = comparisons;
    this.content = content;
    this.assembler = assembler;
    this.preparingAssembler = preparingAssembler;
    this.differenceAssembler = differenceAssembler;
  }

  /**
   * Redirects to the comparison of the act's text in {@code language}, for example {@code eng},
   * between the law on {@code from} and the law on {@code to}. The comparison is at the first days
   * of the versions that apply on the two dates. With {@code amendingAct}, the comparison is
   * limited to the changes of that act, which must be between the two versions.
   *
   * <p>Returns 400 if a parameter is missing or {@code from} is after {@code to}, and 404 if the
   * act has no version in the language on one of the dates.
   */
  @GetMapping(COMPARISONS)
  ResponseEntity<Void> lookup(
      @PathVariable ActIdentifier celex,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to,
      @RequestParam(required = false) @Nullable String language,
      @RequestParam(required = false) @Nullable String amendingAct) {
    if (from == null || to == null || language == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A comparison needs from, to and language.");
    }
    if (from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From is after to.");
    }
    var act = found(celex);
    var in = Requests.language(language);
    var versions = act.timeline().versionsOn(from, to, in).orElseThrow(Requests::notFound);
    var earlier = versions.from();
    var later = versions.to();
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(
            linkTo(
                    methodOn(ApiComparisonController.class)
                        .comparison(celex, earlier.date(), later.date(), language, amendingAct))
                .toUri())
        .build();
  }

  /**
   * Returns the comparison of the act's text in {@code language} between the versions that start on
   * {@code from} and on {@code to}:
   *
   * <ul>
   *   <li>{@code item}: each difference, embedded, in the order of the later text, with the tags of
   *       the consolidations and the amendments that Cellar records at its occurrence. Each links
   *       to the difference whole.
   *   <li>{@code up}: the act.
   *   <li>{@code av:difference}: a difference by the eId of its occurrence, a URI template.
   * </ul>
   *
   * <p>Returns 404 if the dates are not the first days of two versions in the language, in order,
   * if the amending act is not between them, or if a text could not be built.
   */
  @GetMapping(COMPARISON)
  ResponseEntity<RepresentationModel<?>> comparison(
      @PathVariable ActIdentifier celex,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @PathVariable String language,
      @RequestParam(required = false) @Nullable String amendingAct) {
    var ends = ends(celex, from, to, language, amendingAct);
    var texts = ready(ends);
    if (texts == null) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .header(HttpHeaders.RETRY_AFTER, Long.toString(RETRY_AFTER.toSeconds()))
          .body(preparingAssembler.toModel(ends));
    }
    var comparison =
        comparisons.between(texts.earlier(), texts.later()).orElseThrow(Requests::notFound);
    var amended =
        ends.act()
            .amended(
                ends.earlier(),
                ends.later(),
                outline(texts.earlier()).eIds(),
                outline(texts.later()).eIds(),
                ends.language());
    LOG.info(
        "Comparison of {} from {} to {} in {} for the API: {} differences",
        ends.act().id(),
        ends.earlier().date(),
        ends.later().date(),
        ends.language().code(),
        comparison.differences().size());
    return ResponseEntity.ok(assembler.toModel(ends, comparison, amended));
  }

  /**
   * Returns the difference of the comparison at the occurrence {@code eId}, with its occurrence
   * whole and with its edits. Its eId is the rest of the path, for example {@code anx/pnt_1.2}.
   * Returns 404 if the comparison has no difference there. It links to the comparison, {@code up},
   * and to its JSON Schema, {@code describedby}.
   */
  @GetMapping(COMPARISON + "/differences/{*eId}")
  RepresentationModel<?> difference(
      @PathVariable ActIdentifier celex,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @PathVariable String language,
      @PathVariable("eId") String path,
      @RequestParam(required = false) @Nullable String amendingAct) {
    var eId = path.substring(1);
    var ends = ends(celex, from, to, language, amendingAct);
    var texts = ready(ends);
    if (texts == null) {
      throw Requests.notFound();
    }
    var difference =
        comparisons.difference(texts.earlier(), texts.later(), eId).orElseThrow(Requests::notFound);
    var amended =
        ends.act()
            .amended(
                ends.earlier(),
                ends.later(),
                outline(texts.earlier()).eIds(),
                outline(texts.later()).eIds(),
                ends.language());
    return differenceAssembler.toModel(ends, difference, amended, eId);
  }

  private Act found(ActIdentifier celex) {
    return acts.find(celex).orElseThrow(Requests::notFound);
  }

  /** Returns what a canonical address asks for, or throws not found. */
  private Ends ends(
      ActIdentifier celex,
      LocalDate from,
      LocalDate to,
      String language,
      @Nullable String amendingAct) {
    var act = found(celex);
    var in = Requests.language(language);
    var preparation = texts.preparation(act, in);
    return Ends.of(act, in, from, to, amendingAct, preparation, texts.tags(preparation))
        .orElseThrow(Requests::notFound);
  }

  /**
   * Returns the two texts of the comparison, once both are built. Returns null while one is not.
   * Throws not found if one could not be built. It starts the preparation of the act in the
   * language.
   */
  private @Nullable Ready ready(Ends ends) {
    var preparation = texts.prepare(ends.act(), ends.language());
    var earlier = text(preparation.texts().get(ends.earlier()));
    var later = text(preparation.texts().get(ends.later()));
    return earlier == null || later == null ? null : new Ready(earlier, later);
  }

  /** Returns the stored text, or null while it is not built. Throws not found if it failed. */
  private static @Nullable TextId text(@Nullable Text text) {
    return switch (text) {
      case Text.Ready(var id) -> id;
      case Text.Waiting _, Text.Converting _ -> null;
      case Text.Failed _ -> throw Requests.notFound();
      case null -> throw Requests.notFound();
    };
  }

  private Outline outline(TextId text) {
    return content.outline(text).orElseGet(Outline::empty);
  }

  /** The two texts of a comparison, built. */
  private record Ready(TextId earlier, TextId later) {}

  /**
   * A comparison whose texts are not built yet, as the API shows it.
   *
   * @param from the first day of the earlier version
   * @param to the first day of the later version
   * @param language the language, by the EU's code
   */
  record Preparing(LocalDate from, LocalDate to, String language) {

    static Preparing of(Ends ends) {
      return new Preparing(
          ends.earlier().date(), ends.later().date(), code(ends.language().code()));
    }
  }

  /**
   * A comparison, as the API shows it.
   *
   * @param from the first day of the earlier version
   * @param to the first day of the later version
   * @param language the language, by the EU's code. For example, {@code eng}.
   * @param amendingAct the act whose changes it is limited to, or null
   * @param withoutPreamble the text that has no preamble while the other has one, {@code earlier}
   *     or {@code later}. The preamble is then not compared. Null if both or neither have one.
   */
  record ComparisonModel(
      LocalDate from,
      LocalDate to,
      String language,
      @Nullable String amendingAct,
      @Nullable String withoutPreamble) {

    static ComparisonModel of(Ends ends, Comparison comparison) {
      var only = ends.only();
      var without = comparison.withoutPreamble();
      return new ComparisonModel(
          ends.earlier().date(),
          ends.later().date(),
          code(ends.language().code()),
          only == null ? null : only.celex(),
          without == null ? null : code(without.name()));
    }
  }

  /**
   * A difference, as a comparison lists it.
   *
   * @param eId the eId of its occurrence
   * @param kind {@code inserted}, {@code deleted} or {@code changed}
   * @param occurrenceKind the kind of its occurrence in legal-text. For example, {@code article}.
   * @param num the number of its occurrence, as written. For example, {@code (a)}. Empty if none.
   * @param inserted how many words it inserted
   * @param deleted how many words it deleted
   * @param parent the eId of the occurrence that it is in, or null for an outermost occurrence
   * @param section the eId of the occurrence with a rank that it is in, as the outline nests them
   * @param tags the tags of the later text that tell which acts put the occurrence in its form, and
   *     how
   * @param earlierTags the same, in the earlier text
   * @param amendments the amendments that Cellar records at the occurrence between the two versions
   */
  record DifferenceSummary(
      String eId,
      String kind,
      String occurrenceKind,
      String num,
      int inserted,
      int deleted,
      @Nullable String parent,
      @Nullable String section,
      List<TagModel> tags,
      List<TagModel> earlierTags,
      List<AmendmentModel> amendments) {

    static DifferenceSummary of(Act act, Difference difference, Amended amended) {
      return new DifferenceSummary(
          difference.eId(),
          code(difference.kind().name()),
          difference.occurrence().kind(),
          difference.occurrence().num(),
          difference.inserted(),
          difference.deleted(),
          difference.parent(),
          difference.section(),
          difference.tags().stream().map(TagModel::of).toList(),
          difference.earlierTags().stream().map(TagModel::of).toList(),
          amended.of(difference.eId(), difference.kind() != Difference.Kind.CHANGED).stream()
              .map(amendment -> AmendmentModel.of(act, amendment))
              .toList());
    }
  }

  /**
   * A difference whole, as the API shows it.
   *
   * @param difference what the comparison lists of it
   * @param wording the plain text of its occurrence before and after the change, and the runs of
   *     words that stayed, went in and went out
   * @param shown its occurrence, with its edits, in the public model. In its HTML, inserted words
   *     are in {@code ins} and deleted words in {@code del}. Its plain text is the text after the
   *     change.
   */
  record DifferenceModel(
      @JsonUnwrapped DifferenceSummary difference,
      @JsonUnwrapped PublicText.Edited wording,
      PublicText.Occurrence shown) {}

  /**
   * A tag, as the API shows it. For example, ▼M20.
   *
   * @param act the CELEX number of the amending act
   * @param modification what the act did: replaced, inserted, deleted or renumbered
   * @param in the eId of the enclosing occurrence that the act put in its form whole, or null for a
   *     tag of the occurrence itself
   */
  record TagModel(String act, String modification, @Nullable String in) {

    static TagModel of(Difference.Tag tag) {
      return new TagModel(tag.act(), tag.modification().word(), tag.in());
    }
  }

  /**
   * An amendment that Cellar records at an occurrence, as the API shows it.
   *
   * @param act the CELEX number of the amending act
   * @param cited how the Official Journal cites the amending act. For example, {@code 2019/103}.
   * @param location where it acts, as Cellar codes it
   * @param role what it did. For example, {@code replacement}.
   * @param from the day it applies from, as Cellar has it, or null where Cellar has none
   */
  record AmendmentModel(
      String act, String cited, String location, String role, @Nullable LocalDate from) {

    static AmendmentModel of(Act act, Amendment amendment) {
      return new AmendmentModel(
          amendment.act().celex(),
          act.cited(amendment.act()),
          amendment.location().code(),
          code(amendment.role().name()),
          amendment.from());
    }
  }

  private static String code(String text) {
    return text.toLowerCase(java.util.Locale.ROOT);
  }
}
