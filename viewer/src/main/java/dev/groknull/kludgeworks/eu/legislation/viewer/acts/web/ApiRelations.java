// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import org.springframework.hateoas.mediatype.hal.HalLinkRelation;

/**
 * The viewer's own link relations, under its curie ({@code av:versions}); IANA's (self, alternate)
 * are used as they are. HAL curies a link's relation by itself, but not one a resource is embedded
 * by, so every module names its relations here.
 */
public final class ApiRelations {
  /** The curie's name. */
  public static final String CURIE = "av";

  private ApiRelations() {}

  /** The viewer's relation {@code name}, curied: av:versions. */
  public static HalLinkRelation of(String name) {
    return HalLinkRelation.curied(CURIE, name);
  }
}
