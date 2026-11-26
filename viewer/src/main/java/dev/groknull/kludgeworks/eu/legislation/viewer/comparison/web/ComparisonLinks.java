// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Ends;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/** Hypermedia links for comparisons and differences in the API. */
@Component
class ComparisonLinks {

  private final EntityLinks links;

  ComparisonLinks(EntityLinks links) {
    this.links = links;
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
