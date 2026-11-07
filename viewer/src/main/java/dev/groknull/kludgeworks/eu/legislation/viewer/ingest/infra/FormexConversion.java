// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application.Conversions;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Component;

/**
 * Converts the Formex of a document into its legal text. It parses each XML file of the package in
 * the language while it reads the file from the source. Then it converts the package. It writes
 * nothing anywhere. Every call converts again.
 *
 * <p>A conversion holds the files of its package and its result in memory. For the largest package,
 * the 87 MB file of 2016/2070, this takes about 1 GB of heap. Thus only {@code
 * viewer.ingest.max-conversions} conversions run at once, and the others wait their turn. The
 * versions of such an act cannot then run the viewer out of memory together.
 *
 * <p>The conversion logs each document that it converts, with its size and the time to fetch and to
 * convert it. The caller logs a failure where it handles it.
 */
@Component
class FormexConversion implements Conversions {
  private static final Logger LOG = LoggerFactory.getLogger(FormexConversion.class);

  private final FormexSource source;
  private final FormexConverter converter;

  FormexConversion(FormexSource source, FormexConverter converter) {
    this.source = source;
    this.converter = converter;
  }

  /**
   * Returns the legal-text XML of a document. For example, {@code celex} is 32015R1998 or
   * 02015R1998-20260322.
   */
  @Override
  @ConcurrencyLimit(limitString = "${viewer.ingest.max-conversions}")
  public Optional<XdmNode> convert(String celex, Language language) {
    var started = System.nanoTime();
    var documents = source.fetch(celex, language, converter::parse);
    if (documents.isEmpty()) {
      LOG.info("No Formex of {} in {}", celex, language.code());
      return Optional.empty();
    }
    var fetched = System.nanoTime();
    var xml = converter.convert(documents.get(), celex);
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

  @Override
  public String converterVersion() {
    return converter.version();
  }
}
