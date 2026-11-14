// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Release;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
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
  private final Templates templates;
  private final Release release;

  AboutController(Templates templates, Release release) {
    this.templates = templates;
    this.release = release;
  }

  /**
   * Returns the About page. Its version is the release, for example {@code 2026.11.7}. A build from
   * the source has none, and shows the name that {@link Release} gives it, {@code dev-…}.
   */
  @GetMapping(path = "/about", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> about(HttpServletRequest request) {
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .body(templates.catalogueAbout(Chrome.of(request), release.value()).render());
  }
}
