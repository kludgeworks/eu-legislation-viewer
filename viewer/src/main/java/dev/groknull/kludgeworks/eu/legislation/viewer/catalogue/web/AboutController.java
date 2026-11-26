// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Release;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The About page, which the home page's footer links to ("About · Privacy · API"). It says what the
 * viewer is, what it keeps about a reader, what it offers other programs, and which build this is.
 */
/*
 * A handler takes the frame of its page as a Frame, which the site module gives. The check knows
 * only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
@Controller
class AboutController {
  private final Templates templates;
  private final Release release;
  private final ApiDocs docs;

  AboutController(Templates templates, Release release, ApiDocs docs) {
    this.templates = templates;
    this.release = release;
    this.docs = docs;
  }

  /**
   * Returns the About page. Its version is the release, for example {@code 2026.11.7}. A build from
   * the source has none, and shows the name that {@link Release} gives it, {@code dev-…}.
   *
   * <p>The page has the contents of the API's documentation beside it, so the two pages have the
   * same frame.
   */
  @GetMapping(path = "/about", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> about(Frame frame) {
    var sections = docs.read();
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .body(
            templates
                .catalogueAbout(frame, release.value(), sections.resources(), sections.problems())
                .render());
  }
}
