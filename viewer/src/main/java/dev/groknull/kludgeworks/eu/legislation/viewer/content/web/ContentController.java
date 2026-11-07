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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves texts as htmx fragments, by their root. A page transcludes a fragment with {@code hx-get}
 * when its text is built. A root always names the same text, so its fragment is the same wherever
 * it is asked for. The folds of a comparison load some units of a text, by eId, when they open.
 */
@Controller
class ContentController {
  private final Content content;
  private final LegalRenderer html;

  ContentController(Content content, LegalRenderer html) {
    this.content = content;
    this.html = html;
  }

  /** Writes the whole text at {@code root} as HTML. Responds 404 if no text is stored there. */
  @GetMapping(path = "/content/{root}", produces = MediaType.TEXT_HTML_VALUE)
  void text(@PathVariable ContentHash root, HttpServletResponse response) throws IOException {
    var text =
        content.read(root).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    response.setContentType(MediaType.TEXT_HTML_VALUE);
    response.setCharacterEncoding("UTF-8");
    html.element(text).writeTo(new WriterOutput(response.getWriter()));
  }

  /**
   * Writes the units of the text at {@code root} from the unit {@code first} to the unit {@code
   * last}, as the fold of a comparison asks for them. Each unit is whole, in the order of the text.
   * Responds 404 if no text is stored there. Writes nothing if the text does not have {@code
   * first}.
   */
  @GetMapping(path = "/content/{root}/units", produces = MediaType.TEXT_HTML_VALUE)
  void units(
      @PathVariable ContentHash root,
      @RequestParam String first,
      @RequestParam String last,
      HttpServletResponse response)
      throws IOException {
    var text =
        content.read(root).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    response.setContentType(MediaType.TEXT_HTML_VALUE);
    response.setCharacterEncoding("UTF-8");
    var out = new WriterOutput(response.getWriter());
    for (var unit : text.units(first, last)) {
      html.element(unit).writeTo(out);
    }
  }
}
