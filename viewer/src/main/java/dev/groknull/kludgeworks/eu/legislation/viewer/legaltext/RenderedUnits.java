// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import gg.jte.output.StringOutput;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Renders runs of units of a text, for example a window or the fold of a comparison, and keeps them
 * in Valkey ({@link HtmlCacheConfiguration}). To cut a run, the whole text is read and decoded,
 * which takes tenths of a second for the largest texts, so the HTML is kept and the text is read
 * once. The decoded text is not kept.
 */
@Component
class RenderedUnits {
  private static final Logger LOG = LoggerFactory.getLogger(RenderedUnits.class);

  private final Content content;
  private final LegalRenderer html;

  RenderedUnits(Content content, LegalRenderer html) {
    this.content = content;
    this.html = html;
  }

  /**
   * Returns the units of the text from the unit {@code first} to the unit {@code last}, as HTML.
   * Each unit is whole, in the order of the text. Returns empty HTML if the text does not have
   * {@code first}, and none if no such text is stored. Only what is returned is cached.
   */
  @Cacheable(
      cacheNames = HtmlCacheConfiguration.CACHE,
      key = "#text.value() + ':' + #first + ':' + #last",
      unless = "#result == null")
  public Optional<String> units(TextId text, String first, String last) {
    var start = System.nanoTime();
    var read = content.read(text);
    if (read.isEmpty()) {
      return Optional.empty();
    }
    var out = new StringOutput();
    var units = read.get().units(first, last);
    units.forEach(unit -> html.element(unit).writeTo(out));
    var rendered = out.toString();
    LOG.debug(
        "Rendered a run of units of text {}, not cached: {} units, {} KB of HTML, in {} ms",
        text.value(),
        units.size(),
        rendered.length() / 1024,
        (System.nanoTime() - start) / 1_000_000);
    return Optional.of(rendered);
  }
}
