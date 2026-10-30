// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Formex source on the converter's fixtures, in English only: 2015/1998's consolidation of 22
 * March 2026 among them. It records what it was asked for, from any thread.
 */
@AutoBazelRepository
final class FormexFixtures implements FormexSource {
  static final String CONSOLIDATION = "02015R1998-20260322";

  /** The documents fetched, with their language: 02015R1998-20260322 ENG. */
  final List<String> asked = new CopyOnWriteArrayList<>();

  /** The folders fetched into, in every language. */
  final List<Path> folders = new CopyOnWriteArrayList<>();

  @Override
  public boolean fetch(String celex, Language language, Path folder) {
    folders.add(folder);
    if (language != Language.ENG) {
      return false;
    }
    asked.add(celex + " " + language.code());
    try (var files = Files.list(fixture(celex))) {
      for (var file : files.toList()) {
        Files.copy(file, folder.resolve(file.getFileName()));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
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
