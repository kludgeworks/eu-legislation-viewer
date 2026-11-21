// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import org.springframework.hateoas.mediatype.hal.HalLinkRelation;

/**
 * The catalogue's own link relations of the API. A resource uses a relation of the IANA registry
 * where one fits, for example {@code self} or {@code latest-version}. It uses one of these
 * otherwise, under the {@code av} CURIE. For example, {@code av:act}.
 *
 * <p>The name of a relation is in kebab-case, and it is a word of the domain. The address of the
 * CURIE gives the documentation of a relation: {@code /api/docs#act}. Its text is a section of a
 * resource of the API, for example {@code api/resources/02-act.html}, as {@link ApiDocsController}
 * reads it.
 */
enum Relation {
  ACT("act"),
  ACT_BY_CELEX("act-by-celex"),
  ACT_BY_ELI("act-by-eli"),
  AMENDMENTS("amendments"),
  EVENTS("events"),
  ON("on"),
  OUTLINE("outline"),
  PREPARATION("preparation"),
  RETRY("retry"),
  TEXT("text"),
  OCCURRENCE("occurrence"),
  VERSION("version");

  /** The name of the viewer's CURIE, which {@code ApiConfiguration} gives its address. */
  private static final String CURIE = "av";

  private final String localName;

  Relation(String localName) {
    this.localName = localName;
  }

  /** Returns the relation as HAL writes it. For example, {@code av:act}. */
  HalLinkRelation rel() {
    return HalLinkRelation.curied(CURIE, localName);
  }
}
