// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * A Formex source on the converter's fixtures, in English: 2015/1998's consolidation of 22 March
 * 2026 among them; in French a document that isn't a legal act, in German Formex the converter
 * doesn't handle, for any document. It records what it was asked for, from any thread.
 */
@AutoBazelRepository
final class FormexFixtures implements FormexSource {
  static final String CONSOLIDATION = "02015R1998-20260322";

  /** The documents fetched, with their language: 02015R1998-20260322 ENG. */
  final List<String> asked = new CopyOnWriteArrayList<>();

  /** Every fetch, in any language, whether there was Formex or not. */
  final List<String> requests = new CopyOnWriteArrayList<>();

  @Override
  public <T> Optional<List<T>> fetch(
      String celex, Language language, BiFunction<String, InputStream, T> read) {
    requests.add(celex + " " + language.code());
    var refused =
        switch (language) {
          case FRA -> "<JUDGMENT/>";
          case DEU ->
              "<ACT><BIB.INSTANCE><DOCUMENT.REF><COLL>L</COLL></DOCUMENT.REF><LG.DOC>DE</LG.DOC>"
                  + "</BIB.INSTANCE><ENACTING.TERMS><NOT.FORMEX/></ENACTING.TERMS></ACT>";
          default -> null;
        };
    if (refused == null && language != Language.ENG) {
      return Optional.empty();
    }
    asked.add(celex + " " + language.code());
    if (refused != null) {
      var bytes = new ByteArrayInputStream(refused.getBytes(StandardCharsets.UTF_8));
      return Optional.of(List.of(read.apply(celex + ".xml", bytes)));
    }
    var documents = new ArrayList<T>();
    try (var files = Files.list(fixture(celex))) {
      for (var file : files.filter(f -> f.toString().endsWith(".xml")).sorted().toList()) {
        try (var in = Files.newInputStream(file)) {
          documents.add(read.apply(file.getFileName().toString(), in));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Optional.of(documents);
  }

  private static Path fixture(String celex) throws IOException {
    return Path.of(
        Runfiles.preload()
            .withSourceRepository(AutoBazelRepository_FormexFixtures.NAME)
            .rlocation(
                "kludgeworks_viewer/formex-converter/src/test/resources/fixtures/formex/06.02.1/"
                    + celex));
  }
}
