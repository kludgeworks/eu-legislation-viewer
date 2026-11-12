// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The About page, which the home page's footer links to ("About · Privacy · API"). It says what the
 * viewer is, what it keeps about a reader, what it offers other programs, and which build this is.
 */
@Controller
class AboutController {
  /** The version of a build that has none in its jar's manifest, as one run from the source has. */
  static final String UNVERSIONED = "development build";

  private final Templates templates;

  AboutController(Templates templates) {
    this.templates = templates;
  }

  /** Returns the About page. */
  @GetMapping(path = "/about", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> about(HttpServletRequest request) {
    var version =
        Objects.requireNonNullElse(
            AboutController.class.getPackage().getImplementationVersion(), UNVERSIONED);
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .body(templates.catalogueAbout(Chrome.of(request), version).render());
  }
}
