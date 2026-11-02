// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.time.Duration;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Component;

/**
 * A document's legal text converted from its Formex: each XML file of its package in the language
 * parsed as it is read from the source, then converted; nothing is written anywhere. Every call
 * converts.
 *
 * <p>A conversion holds its package's files and its result in memory, which for the largest
 * (2016/2070's 87 MB file) takes about 1 GB of heap: only so many run at once
 * (viewer.ingest.max-conversions), the others waiting their turn, so the versions of such an act
 * can't run the viewer out of memory together.
 *
 * <p>Each document converted is logged, with its size and how long fetching and converting took; a
 * failure is the caller's to log, where it is handled.
 */
@Component
class FormexConversion {
  private static final Logger LOG = LoggerFactory.getLogger(FormexConversion.class);

  private final FormexSource source;
  private final FormexConverter converter;

  FormexConversion(FormexSource source, FormexConverter converter) {
    this.source = source;
    this.converter = converter;
  }

  /**
   * The legal-text XML of a document ({@code celex}: 32015R1998, 02015R1998-20260322) in a
   * language.
   *
   * @return none if Cellar has no Formex of the document in the language
   */
  @ConcurrencyLimit(limitString = "${viewer.ingest.max-conversions}")
  Optional<XdmNode> convert(String celex, Language language) {
    var started = System.nanoTime();
    var documents = source.fetch(celex, language, converter::parse);
    if (documents.isEmpty()) {
      LOG.info("No Formex of {} in {}", celex, language.code());
      return Optional.empty();
    }
    var fetched = System.nanoTime();
    var xml = converter.convert(documents.get(), Optional.of(celex));
    LOG.info(
        "Converted {} in {}: {} KB of Formex in {} file(s), fetched in {} ms, converted in {} ms",
        celex,
        language.code(),
        documents.get().stream().mapToLong(FormexConverter.Document::size).sum() / 1024,
        documents.get().size(),
        Duration.ofNanos(fetched - started).toMillis(),
        Duration.ofNanos(System.nanoTime() - fetched).toMillis());
    return Optional.of(xml);
  }

  /** The converter's version: what the legal text is made with. */
  String converterVersion() {
    return converter.version();
  }
}
