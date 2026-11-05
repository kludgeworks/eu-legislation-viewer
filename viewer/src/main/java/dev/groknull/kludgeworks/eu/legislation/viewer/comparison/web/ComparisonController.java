// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer;
import gg.jte.generated.precompiled.Templates;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * A comparison's Changes view, by the two texts' roots: /comparison/{from}/{to}, the earlier first,
 * worked out now if no one has yet (Comparisons); 404 if either text isn't stored. A page
 * transcludes it, as it does a text from the content module.
 */
@Controller
class ComparisonController {
  private final Comparisons comparisons;
  private final Templates templates;
  private final LegalRenderer html;

  ComparisonController(Comparisons comparisons, Templates templates, LegalRenderer html) {
    this.comparisons = comparisons;
    this.templates = templates;
    this.html = html;
  }

  /** The Changes view of the text at {@code from} against the one at {@code to}. */
  @GetMapping(path = "/comparison/{from}/{to}", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String changes(@PathVariable ContentHash from, @PathVariable ContentHash to) {
    var comparison =
        comparisons
            .between(from, to)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return templates
        .comparisonChanges(ChangesView.of(comparison), html, "/content/" + to.hex() + "/units")
        .render();
  }
}
