// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.zip.CRC32;

/**
 * A short mark of one attempt at building an expression (Design D-62): four hex digits, the same in
 * the attempt's log lines (MDC trace) and in the reference a failure panel asks the reader to
 * quote, so a report finds its lines: 32015R1998 · eng · 7f3c.
 */
public final class Trace {
  private Trace() {}

  /** The mark of {@code celex}'s attempt {@code attempt} in {@code language}. */
  public static String of(String celex, Language language, int attempt) {
    var crc = new CRC32();
    crc.update((celex + "/" + language.code() + "/" + attempt).getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().toHexDigits((short) crc.getValue());
  }
}
