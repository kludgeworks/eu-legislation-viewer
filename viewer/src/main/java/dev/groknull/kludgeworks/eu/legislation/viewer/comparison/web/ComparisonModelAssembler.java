// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.DIFFERENCE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Ends;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.ComparisonModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.DifferenceModel;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.DifferenceSummary;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.ApiComparisonController.Preparing;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.ApiLinks;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/** Assembles the hypermedia representations of comparisons and differences in the API. */
@Component
class ComparisonModelAssembler
    implements RepresentationModelAssembler<Ends, RepresentationModel<?>> {

  /** The JSON Schema of a difference, in version 1 of the API. */
  static final String DIFFERENCE_SCHEMA = "/api/schemas/v1/difference.schema.json";

  /** The answer of a ready comparison between two versions. */
  record Compared(Ends ends, Comparison comparison, Amended amended) {}

  /** The answer of a difference at an occurrence. */
  record Differed(Ends ends, Difference difference, Amended amended, String eId) {}

  private final Content content;
  private final EntityLinks links;

  ComparisonModelAssembler(Content content, EntityLinks links) {
    this.content = content;
    this.links = links;
  }

  /**
   * Returns the representation of a comparison whose texts are still being prepared: links to
   * {@code self} and {@code up} (the act).
   */
  @Override
  public RepresentationModel<?> toModel(Ends ends) {
    var celex = ends.act().id();
    var self = at(ends, IanaLinkRelations.SELF);
    return HalModelBuilder.halModelOf(Preparing.of(ends)).link(self).link(up(celex)).build();
  }

  /**
   * Returns the representation of a comparison whole: each difference embedded as {@code item},
   * {@code self}, {@code up} (the act), and {@code av:difference} (URI template).
   */
  public RepresentationModel<?> toComparisonModel(Compared compared) {
    var ends = compared.ends();
    var comparison = compared.comparison();
    var amended = compared.amended();
    var celex = ends.act().id();
    var self = at(ends, IanaLinkRelations.SELF);
    return HalModelBuilder.halModelOf(ComparisonModel.of(ends, comparison))
        .embed(
            comparison.differences().stream()
                .map(difference ->
                    EntityModel.of(DifferenceSummary.of(ends.act(), difference, amended))
                        .add(Link.of(differenceHref(self, difference.eId()))))
                .toList(),
            IanaLinkRelations.ITEM)
        .link(self)
        .link(up(celex))
        .link(Link.of(differenceTemplate(self), DIFFERENCE))
        .build();
  }

  /**
   * Returns the representation of a single difference at {@code eId}: links to its canonical
   * address, {@code up} (the comparison), and {@code describedby} schema.
   */
  public RepresentationModel<?> toDifferenceModel(Differed differed) {
    var ends = differed.ends();
    var difference = differed.difference();
    var amended = differed.amended();
    var eId = differed.eId();
    var comparison = at(ends, IanaLinkRelations.UP);
    return HalModelBuilder.halModelOf(
            new DifferenceModel(
                DifferenceSummary.of(ends.act(), difference, amended),
                content.publicWording(difference.shown()),
                content.publicOccurrence(difference.shown())))
        .link(Link.of(differenceHref(comparison, eId)))
        .link(comparison)
        .link(ApiLinks.schema(DIFFERENCE_SCHEMA))
        .build();
  }

  Link at(Ends ends, LinkRelation relation) {
    return at(
        ends.act().id(),
        ends.from().date(),
        ends.to().date(),
        code(ends.language().code()),
        ends.only() == null ? null : ends.only().celex(),
        relation);
  }

  /**
   * Returns a link to the comparison. It has {@code amendingAct} only if it is given, not as a
   * template variable.
   */
  Link at(
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

  Link up(ActIdentifier celex) {
    return links.linkToItemResource(Act.class, celex).withRel(IanaLinkRelations.UP);
  }

  /** Returns the template of a difference of a comparison. */
  static UriTemplate differenceTemplate(Link comparison) {
    var href = comparison.getHref();
    var query = href.indexOf('?');
    var path = query < 0 ? href : href.substring(0, query);
    return UriTemplate.of(path + "/differences/{+eId}" + (query < 0 ? "" : href.substring(query)));
  }

  /** Returns the address of the difference at {@code eId} of a comparison. Its {@code /} stays. */
  static String differenceHref(Link comparison, String eId) {
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
}
