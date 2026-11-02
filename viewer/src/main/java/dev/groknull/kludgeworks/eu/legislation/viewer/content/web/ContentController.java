// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import gg.jte.output.WriterOutput;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Texts as htmx fragments, by their root: what a page transcludes (hx-get) once its text is built.
 * A root names one text for good, so its fragment is the same wherever it is asked for.
 */
@Controller
class ContentController {
  private final Content content;
  private final LegalRenderer html;

  ContentController(Content content, LegalRenderer html) {
    this.content = content;
    this.html = html;
  }

  /** The whole text at {@code root}, as HTML; 404 if none is stored there. */
  @GetMapping(path = "/content/{root}", produces = MediaType.TEXT_HTML_VALUE)
  void text(@PathVariable ContentHash root, HttpServletResponse response) throws IOException {
    var text =
        content.read(root).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    response.setContentType(MediaType.TEXT_HTML_VALUE);
    response.setCharacterEncoding("UTF-8");
    html.element(text).writeTo(new WriterOutput(response.getWriter()));
  }
}
