// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
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
 * A Formex source on the converter's fixtures, in English: 2015/1998's consolidations of 2026 among
 * them, none for a document without one; in French a document that isn't a legal act, in German
 * Formex the converter doesn't handle, in Italian another version's Formex (as Cellar misfiles it),
 * for any document. It records what it was asked for, from any thread. With the converter's
 * expected output for the fixtures.
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
              "<CONS.ACT><INFO.CONSLEG CONSLEG.REF=\"2015R1998\""
                  + " START.DATE=\"20260322\"/><CONS.DOC>"
                  + "<BIB.INSTANCE><LG.DOC>DE</LG.DOC></BIB.INSTANCE>"
                  + "<ENACTING.TERMS><NOT.FORMEX/></ENACTING.TERMS></CONS.DOC></CONS.ACT>";
          case ITA ->
              "<CONS.ACT><INFO.CONSLEG CONSLEG.REF=\"2015R1998\""
                  + " START.DATE=\"20160101\"/><CONS.DOC>"
                  + "<BIB.INSTANCE><LG.DOC>IT</LG.DOC></BIB.INSTANCE>"
                  + "<ENACTING.TERMS/></CONS.DOC></CONS.ACT>";
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
    try {
      if (!Files.isDirectory(fixture(celex))) {
        // a version the converter has no fixture of: as Cellar has none of a text without Formex
        return Optional.empty();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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

  /** The converter's expected output for a fixture, as it is written: 02015R1998-20260322. */
  static String expected(String celex) {
    try {
      return Files.readString(resource("expected/formex/06.02.1/" + celex + ".xml"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path fixture(String celex) throws IOException {
    return resource("fixtures/formex/06.02.1/" + celex);
  }

  private static Path resource(String path) throws IOException {
    return Path.of(
        Runfiles.preload()
            .withSourceRepository(AutoBazelRepository_FormexFixtures.NAME)
            .rlocation("kludgeworks_viewer/formex-converter/src/test/resources/" + path));
  }
}
