// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.api.ApiLinks;
import java.time.LocalDate;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;

/**
 * Links to an act's resources (URLs and API: public API): /api/acts/{celex}, its versions by their
 * dates and their texts by language tag; through ApiLinks, so a treaty's CELEX is one segment
 * (/api/acts/12016E%2FTXT).
 */
public final class ActLinks {
  /** The name of an alternate on EUR-Lex: the official text, or its document page. */
  public static final String EUR_LEX = "eur-lex";

  /** The name of an alternate in Cellar: the work, or the expression. */
  public static final String CELLAR = "cellar";

  private ActLinks() {}

  /** The act: /api/acts/32015R1998. */
  public static Link act(ActIdentifier act, LinkRelation relation) {
    return ApiLinks.to(relation, "acts", act.celex());
  }

  /** Its versions, or with {@code on} the one that applies on a date: /api/acts/…/versions{?on}. */
  public static Link versions(ActIdentifier act, LinkRelation relation) {
    var href = act(act, relation).getHref() + "/versions{?on}";
    return Link.of(href, relation);
  }

  /** One version, by the date it applies from: /api/acts/32015R1998/versions/2026-03-22. */
  public static Link version(ActIdentifier act, LocalDate date, LinkRelation relation) {
    return ApiLinks.to(relation, "acts", act.celex(), "versions", date.toString());
  }

  /** A version's text in {@code language}: …/versions/2026-03-22/texts/en. */
  public static Link text(
      ActIdentifier act, LocalDate date, Language language, LinkRelation relation) {
    return ApiLinks.to(
        relation, "acts", act.celex(), "versions", date.toString(), "texts", language.iso());
  }

  /** Comparing two of its versions, by any two dates: /api/acts/…/comparisons{?from,to}. */
  public static Link compare(ActIdentifier act, LinkRelation relation) {
    return Link.of(act(act, relation).getHref() + "/comparisons{?from,to}", relation);
  }

  /** Comparing two of its versions, by their dates: …/comparisons/2025-11-04..2026-03-22. */
  public static Link comparison(
      ActIdentifier act, LocalDate from, LocalDate to, LinkRelation relation) {
    return ApiLinks.to(relation, "acts", act.celex(), "comparisons", from + ".." + to);
  }

  /** Its amendments: /api/acts/…/amendments. */
  public static Link amendments(ActIdentifier act, LinkRelation relation) {
    return ApiLinks.to(relation, "acts", act.celex(), "amendments");
  }

  /** A version as a summary in a list, with its link (never the text). */
  public static EntityModel<Version> summary(ActIdentifier act, Version version) {
    return EntityModel.of(version, version(act, version.date(), IanaLinkRelations.SELF));
  }
}
