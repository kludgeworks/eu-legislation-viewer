// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Release;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Requests;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Serves texts as htmx fragments, by their identity. A page transcludes a fragment with {@code
 * hx-get} when its text is built. An identity always names the same text, so its fragment is the
 * same wherever it is asked for. The folds of a comparison load some units of a text, by eId, when
 * they open. The HTML is cached in Valkey ({@link RenderedTexts}).
 *
 * <p>An identity is never used again, but the HTML of a text changes with the release. Thus a
 * browser asks again each time ({@link Release#REVALIDATED}), with an entity tag of the release and
 * the identity. A request with that tag in {@code If-None-Match} gets 304, and nothing is read or
 * rendered. A request for a text that is no longer stored, for example one that was replaced, gets
 * 404.
 */
@Controller
class LegalTextController {
  private static final Logger LOG = LoggerFactory.getLogger(LegalTextController.class);

  private final Content content;
  private final RenderedTexts texts;
  private final Release release;

  LegalTextController(Content content, RenderedTexts texts, Release release) {
    this.content = content;
    this.texts = texts;
    this.release = release;
  }

  /** Writes the whole text {@code id} as HTML. Responds 404 if no such text is stored. */
  @GetMapping(path = "/content/{id}", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  @Nullable String text(
      @PathVariable TextId id, HttpServletRequest request, HttpServletResponse response) {
    if (notModified(id, request, response)) {
      return null;
    }
    var start = System.nanoTime();
    var rendered = texts.whole(id).orElseThrow(Requests::notFound);
    LOG.debug(
        "Whole text {}: {} KB of HTML, in {} ms",
        id.value(),
        rendered.length() / 1024,
        (System.nanoTime() - start) / 1_000_000);
    return rendered;
  }

  /**
   * Writes the units of the text {@code id} from the unit {@code first} to the unit {@code last},
   * as the fold of a comparison asks for them. Each unit is whole, in the order of the text.
   * Responds 404 if no such text is stored. Writes nothing if the text does not have {@code first}.
   */
  @GetMapping(path = "/content/{id}/units", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  @Nullable String units(
      @PathVariable TextId id,
      @RequestParam String first,
      @RequestParam String last,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (notModified(id, request, response)) {
      return null;
    }
    var start = System.nanoTime();
    var rendered = texts.units(id, first, last).orElseThrow(Requests::notFound);
    LOG.debug(
        "Units of text {}: {} KB of HTML, in {} ms",
        id.value(),
        rendered.length() / 1024,
        (System.nanoTime() - start) / 1_000_000);
    return rendered;
  }

  /**
   * Answers 404 if the text {@code id} is not stored, and 304 if the browser has the response
   * already. Returns whether it answered 304. A cached response is never served for a text that is
   * no longer stored.
   */
  private boolean notModified(TextId id, HttpServletRequest request, HttpServletResponse response) {
    if (!content.contains(id)) {
      throw Requests.notFound();
    }
    return Release.notModified(request, response, Release.REVALIDATED, release.etag(id));
  }
}
