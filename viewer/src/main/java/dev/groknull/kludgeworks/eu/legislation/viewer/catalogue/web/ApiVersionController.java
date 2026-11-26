// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.VERSION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.VERSIONS;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.VersionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The versions of an act in the API, and each version. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiVersionController {
  private final Catalogued catalogued;
  private final VersionModelAssembler assembler;
  private final VersionsModelAssembler versionsAssembler;

  ApiVersionController(
      Catalogued catalogued,
      VersionModelAssembler assembler,
      VersionsModelAssembler versionsAssembler) {
    this.catalogued = catalogued;
    this.assembler = assembler;
    this.versionsAssembler = versionsAssembler;
  }

  /**
   * Returns the versions of the act, in date order:
   *
   * <ul>
   *   <li>{@code item}: each version, embedded.
   *   <li>{@code first}: the original text. The versions are a series, and they are not paged.
   *   <li>{@code latest-version}: as on the act.
   * </ul>
   */
  @GetMapping(VERSIONS)
  RepresentationModel<?> versions(@PathVariable ActIdentifier celex) {
    return versionsAssembler.toModel(catalogued.api(celex));
  }

  /**
   * Returns the version at {@code key}: {@code oj} for the original text, or the date of a
   * consolidation. Returns 404 for any other key. It links to the versions before and after it
   * among all the versions of the act, in any language. It links to its text in each of its
   * languages, {@code av:text}, with the language in {@code hreflang}.
   */
  @GetMapping(VERSION)
  EntityModel<VersionModel> version(@PathVariable ActIdentifier celex, @PathVariable String key) {
    var act = catalogued.api(celex);
    var version = ApiActResources.at(act.timeline().versions(), key);
    return assembler.toModel(new VersionModelAssembler.ActVersion(act, version));
  }
}
