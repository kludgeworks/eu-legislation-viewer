// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;

/**
 * Converts the manifestations a list names, from the corpus mirror, into one folder: what
 * text:survey renders and compares with EUR-Lex. The list has a manifestation a line, its Cellar ID
 * and its CELEX number, tab-separated; each becomes {CELEX}.xml, and conversions.tsv says how each
 * went (CELEX, Cellar ID, ok or the stylesheet's error code, milliseconds, KB of Formex).
 *
 * <pre>
 * bazel run …:convert_texts -- {mirror} {list} {folder}
 * </pre>
 */
public final class ConvertTexts {

  private ConvertTexts() {}

  public static void main(String[] args) throws IOException, SaxonApiException {
    if (args.length != 3) {
      throw new IllegalArgumentException("usage: convert_texts {mirror} {list} {folder}");
    }
    var mirror = Path.of(args[0]);
    var out = Files.createDirectories(Path.of(args[2]));
    var saxon = new Processor(false);
    var stylesheet = new Stylesheet(saxon);
    var log = new StringBuilder();
    var converted = 0;
    for (var line : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
      var fields = line.split("\t", -1);
      if (fields.length < 2 || fields[0].isBlank()) {
        continue;
      }
      var id = fields[0];
      var celex = fields[1];
      var manifestation = new Manifestation(id, mirror.resolve("files").resolve(id));
      var size = size(manifestation.folder()) / 1024;
      var started = System.nanoTime();
      String status;
      try {
        var legalText = stylesheet.transform(manifestation.source(), Optional.of(celex));
        Files.writeString(
            out.resolve(celex + ".xml"),
            ExpectedOutput.serialize(saxon, legalText),
            StandardCharsets.UTF_8);
        status = "ok";
        converted++;
      } catch (Stylesheet.Failure e) {
        status = e.code() == null ? "failed" : e.code().getLocalName();
      }
      var millis = (System.nanoTime() - started) / 1_000_000;
      log.append(String.join("\t", celex, id, status, Long.toString(millis), Long.toString(size)))
          .append('\n');
      System.out.println(celex + ": " + status + " in " + millis + " ms (" + size + " KB)");
    }
    Files.writeString(out.resolve("conversions.tsv"), log, StandardCharsets.UTF_8);
    System.out.println(converted + " converted into " + out);
  }

  /** The bytes of a package's files, as the mirror keeps them. */
  private static long size(Path folder) throws IOException {
    try (var files = Files.list(folder)) {
      var total = 0L;
      for (var file : files.toList()) {
        total += Files.size(file);
      }
      return total;
    }
  }
}
