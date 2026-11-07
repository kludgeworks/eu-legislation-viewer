// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.SourceUnavailableException;
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
import org.springframework.web.client.ResourceAccessException;

/**
 * A Formex source on the converter's fixtures. It answers as follows, by language:
 *
 * <ul>
 *   <li>English: the fixtures, which include the consolidations of 2015/1998 of 2026. There is no
 *       Formex for a document that has no fixture.
 *   <li>French: a document that is not a legal act, for any document.
 *   <li>German: Formex that the converter does not handle, for any document.
 *   <li>Italian: the Formex of another version, for any document. Cellar sometimes files Formex
 *       like this.
 *   <li>Swedish: Cellar does not answer.
 *   <li>Other languages: no Formex.
 * </ul>
 *
 * <p>It records what it was asked for, from any thread. It also gives the converter's expected
 * output for the fixtures.
 */
@AutoBazelRepository
final class FormexFixtures implements FormexSource {
  static final String CONSOLIDATION = "02015R1998-20260322";

  /**
   * The documents fetched, each with its language. For example: {@code 02015R1998-20260322 ENG}.
   */
  final List<String> asked = new CopyOnWriteArrayList<>();

  /** Every fetch, in any language, with or without Formex. */
  final List<String> requests = new CopyOnWriteArrayList<>();

  @Override
  public <T> Optional<List<T>> fetch(
      String celex, Language language, BiFunction<String, InputStream, T> read) {
    requests.add(celex + " " + language.code());
    if (language == Language.SWE) {
      throw new SourceUnavailableException(
          "Cellar didn't answer for " + celex + " in SWE",
          new ResourceAccessException("I/O error: Read timed out"));
    }
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
        // The converter has no fixture of this version. Cellar also has nothing for a text
        // without Formex.
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

  /**
   * The converter's expected output for the fixture {@code celex}, as it is written. For example:
   * {@code 02015R1998-20260322}.
   */
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
