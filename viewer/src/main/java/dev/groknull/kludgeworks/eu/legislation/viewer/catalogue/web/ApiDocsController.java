// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The documentation of the API for people, on one page, {@code /api/docs}: how the API answers,
 * each resource with its members and links, and each problem type. Each module gives its section
 * ({@link ApiDocs}).
 *
 * <ul>
 *   <li>The {@code av} CURIE of a HAL document leads to a relation on the page: {@code
 *       /api/docs#act}. A relation that leads to a resource has the anchor of the resource. The
 *       others, a search or an action, have theirs in the links of the resource that has them. The
 *       {@code service-doc} link of the root leads to the page.
 *   <li>The {@code type} of a problem leads to a problem type on the page: {@code
 *       /api/docs#text-failed}.
 * </ul>
 */
/*
 * A handler takes the frame of its page as a Frame, which the site module gives. The check knows
 * only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
@Controller
class ApiDocsController {
  /** The address of the page. */
  static final String DOCS = ApiRootController.ROOT + "/docs";

  private final Templates templates;
  private final ApiDocs docs;

  ApiDocsController(Templates templates, ApiDocs docs) {
    this.templates = templates;
    this.docs = docs;
  }

  /** Returns the page. */
  @GetMapping(path = DOCS, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> docs(Frame frame) {
    var sections = docs.read();
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .body(
            templates
                .catalogueApi(frame, sections.resources(), sections.examples(), sections.problems())
                .render());
  }
}
