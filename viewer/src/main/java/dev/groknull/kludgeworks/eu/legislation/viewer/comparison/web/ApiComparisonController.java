// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Requests;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.hateoas.server.EntityLinks;
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
import org.springframework.web.util.UriUtils;

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
  private final EntityLinks links;

  ApiComparisonController(
      Acts acts, Texts texts, Comparisons comparisons, Content content, EntityLinks links) {
    this.acts = acts;
    this.texts = texts;
    this.comparisons = comparisons;
    this.content = content;
    this.links = links;
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
    var earlier = act.versionOn(from, in).orElseThrow(Requests::notFound);
    var later = act.versionOn(to, in).orElseThrow(Requests::notFound);
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
   *   <li>{@code item}: each difference, embedded, in the order of the later text, with the
   *       amendment markers of the consolidations and the amendments that Cellar records at its
   *       unit. Each links to the difference whole.
   *   <li>{@code up}: the act.
   *   <li>{@code av:difference}: a difference by the eId of its unit, a URI template.
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
    var asked = asked(celex, from, to, language, amendingAct);
    var self = at(celex, from, to, language, amendingAct, IanaLinkRelations.SELF);
    var texts = ready(asked);
    if (texts == null) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .header(HttpHeaders.RETRY_AFTER, Long.toString(RETRY_AFTER.toSeconds()))
          .body(HalModelBuilder.halModelOf(Pending.of(asked)).link(self).link(up(celex)).build());
    }
    var comparison =
        comparisons.between(texts.earlier(), texts.later()).orElseThrow(Requests::notFound);
    var amended =
        asked.ends.act.amended(
            asked.earlier,
            asked.later,
            outline(texts.earlier()).eIds(),
            outline(texts.later()).eIds(),
            asked.ends.language);
    LOG.info(
        "Comparison of {} from {} to {} in {} for the API: {} differences",
        asked.ends.act.id(),
        asked.earlier.date(),
        asked.later.date(),
        asked.ends.language.code(),
        comparison.differences().size());
    return ResponseEntity.ok(
        HalModelBuilder.halModelOf(ComparisonModel.of(asked, comparison))
            .embed(
                comparison.differences().stream()
                    .map(
                        difference ->
                            EntityModel.of(
                                    DifferenceSummary.of(asked.ends.act, difference, amended))
                                .add(Link.of(differenceHref(self, difference.eId()))))
                    .toList(),
                IanaLinkRelations.ITEM)
            .link(self)
            .link(up(celex))
            .link(Link.of(UriTemplate.of(self.getHref() + "/differences/{+eId}"), DIFFERENCE))
            .build());
  }

  /**
   * Returns the difference of the comparison at the unit {@code eId}, with its unit whole and
   * marked. Its eId is the rest of the path, for example {@code anx/pnt_1.2}. Returns 404 if the
   * comparison has no difference there.
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
    var asked = asked(celex, from, to, language, amendingAct);
    var texts = ready(asked);
    if (texts == null) {
      throw Requests.notFound();
    }
    var difference =
        comparisons.difference(texts.earlier(), texts.later(), eId).orElseThrow(Requests::notFound);
    var amended =
        asked.ends.act.amended(
            asked.earlier,
            asked.later,
            outline(texts.earlier()).eIds(),
            outline(texts.later()).eIds(),
            asked.ends.language);
    var comparison = at(celex, from, to, language, amendingAct, IanaLinkRelations.UP);
    return HalModelBuilder.halModelOf(
            new DifferenceModel(
                DifferenceSummary.of(asked.ends.act, difference, amended),
                new String(content.json(difference.shown()), StandardCharsets.UTF_8)))
        .link(Link.of(differenceHref(comparison, eId)))
        .link(comparison)
        .build();
  }

  /**
   * Returns a link to the comparison. It has {@code amendingAct} only if it is given, not as a
   * template variable.
   */
  private static Link at(
      ActIdentifier celex,
      LocalDate from,
      LocalDate to,
      String language,
      @Nullable String amendingAct,
      LinkRelation relation) {
    return Link.of(
        linkTo(
                methodOn(ApiComparisonController.class)
                    .comparison(celex, from, to, language, amendingAct))
            .toUri()
            .toString(),
        relation);
  }

  private Act found(ActIdentifier celex) {
    return acts.find(celex).orElseThrow(Requests::notFound);
  }

  private Link up(ActIdentifier celex) {
    return links.linkToItemResource(Act.class, celex).withRel(IanaLinkRelations.UP);
  }

  /** Returns what a canonical address asks for, or throws not found. */
  private Asked asked(
      ActIdentifier celex,
      LocalDate from,
      LocalDate to,
      String language,
      @Nullable String amendingAct) {
    var ends = new Ends(found(celex), Requests.language(language), from, to, amendingAct);
    var only = ends.only;
    var later = only == null ? ends.to : ends.step(only).orElseThrow(Requests::notFound);
    var earlier = only == null ? ends.from : ends.earlier(later);
    return new Asked(ends, earlier, later);
  }

  /**
   * Returns the two texts of the comparison, once both are built. Returns null while one is not.
   * Throws not found if one could not be built. It starts the preparation of the act in the
   * language.
   */
  private @Nullable Ready ready(Asked asked) {
    var preparation = texts.prepare(asked.ends.act, asked.ends.language);
    var earlier = text(preparation.texts().get(asked.earlier));
    var later = text(preparation.texts().get(asked.later));
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

  /** Returns the address of the difference at {@code eId} of a comparison. Its {@code /} stays. */
  private static String differenceHref(Link comparison, String eId) {
    var href = comparison.getHref();
    var query = href.indexOf('?');
    var path = query < 0 ? href : href.substring(0, query);
    return path
        + "/differences/"
        + Arrays.stream(eId.split("/", -1))
            .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
            .collect(Collectors.joining("/"))
        + (query < 0 ? "" : href.substring(query));
  }

  private static String code(String code) {
    return code.toLowerCase(Locale.ROOT);
  }

  /**
   * What a canonical address asks for: its ends, and the two versions compared. With an amending
   * act, these are the version that integrates it and the version before.
   */
  private record Asked(Ends ends, Version earlier, Version later) {}

  /** The two texts of a comparison, built. */
  private record Ready(TextId earlier, TextId later) {}

  /**
   * A comparison whose texts are not built yet, as the API shows it.
   *
   * @param from the first day of the earlier version
   * @param to the first day of the later version
   * @param language the language, by the EU's code
   */
  record Pending(LocalDate from, LocalDate to, String language) {

    static Pending of(Asked asked) {
      return new Pending(
          asked.earlier.date(), asked.later.date(), code(asked.ends.language.code()));
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

    static ComparisonModel of(Asked asked, Comparison comparison) {
      var only = asked.ends.only;
      var without = comparison.withoutPreamble();
      return new ComparisonModel(
          asked.earlier.date(),
          asked.later.date(),
          code(asked.ends.language.code()),
          only == null ? null : only.celex(),
          without == null ? null : code(without.name()));
    }
  }

  /**
   * A difference, as a comparison lists it.
   *
   * @param eId the eId of its unit
   * @param kind {@code inserted}, {@code deleted} or {@code changed}
   * @param unitKind the kind of its unit in legal-text. For example, {@code article}.
   * @param num the number of its unit, as written. For example, {@code (a)}. Empty if none.
   * @param inserted how many words it inserted
   * @param deleted how many words it deleted
   * @param parent the eId of the unit that it is in, or null for an outermost unit
   * @param section the eId of the unit with a rank that it is in, as the outline nests them
   * @param markers the amendment markers of the later text that tell which acts put the unit in its
   *     form, and how
   * @param earlierMarkers the same, in the earlier text
   * @param amendments the amendments that Cellar records at the unit between the two versions
   */
  record DifferenceSummary(
      String eId,
      String kind,
      String unitKind,
      String num,
      int inserted,
      int deleted,
      @Nullable String parent,
      @Nullable String section,
      List<MarkerModel> markers,
      List<MarkerModel> earlierMarkers,
      List<AmendmentModel> amendments) {

    static DifferenceSummary of(Act act, Difference difference, Amended amended) {
      return new DifferenceSummary(
          difference.eId(),
          code(difference.kind().name()),
          difference.unit().kind(),
          difference.unit().num(),
          difference.inserted(),
          difference.deleted(),
          difference.parent(),
          difference.section(),
          difference.markers().stream().map(MarkerModel::of).toList(),
          difference.earlierMarkers().stream().map(MarkerModel::of).toList(),
          amended.of(difference.eId(), difference.kind() != Difference.Kind.CHANGED).stream()
              .map(amendment -> AmendmentModel.of(act, amendment))
              .toList());
    }
  }

  /**
   * A difference whole, as the API shows it.
   *
   * @param difference what the comparison lists of it
   * @param shown its unit, marked, JSON in the JsonML form of the legal-text schema. Inserted words
   *     are in {@code diff:ins} and deleted words in {@code diff:del}.
   */
  record DifferenceModel(@JsonUnwrapped DifferenceSummary difference, @JsonRawValue String shown) {}

  /**
   * An amendment marker, as the API shows it. For example, ▼M20.
   *
   * @param act the CELEX number of the amending act
   * @param action what the act did: replaced, inserted, deleted or renumbered
   * @param in the eId of the enclosing unit that the act put in its form whole, or null for a
   *     marker of the unit itself
   */
  record MarkerModel(String act, String action, @Nullable String in) {

    static MarkerModel of(Difference.Marker marker) {
      return new MarkerModel(marker.act(), marker.action(), marker.in());
    }
  }

  /**
   * An amendment that Cellar records at a unit, as the API shows it.
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
}
