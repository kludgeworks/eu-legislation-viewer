// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The fixtures, as argument sources for parameterized tests: raw Formex as Cellar serves it, under
 * formex-converter/test/fixtures (the test target's data).
 */
@AutoBazelRepository
final class Fixtures {

  private static final Path ROOT = root();

  private Fixtures() {}

  /**
   * Documents in scope: one folder per document under its Formex version,
   * formex/&lt;version&gt;/&lt;CELEX&gt;.
   */
  static Stream<Fixture> inScope() {
    return folders("formex", 2);
  }

  /** One fixture by its folder: formex/06.02.1/02015R1998-20260322. */
  static Fixture named(String name) {
    var folder = ROOT.resolve(name);
    if (!Files.isDirectory(folder)) {
      throw new IllegalArgumentException("no fixture " + name);
    }
    return new Fixture(name, folder);
  }

  /** Documents out of scope: one folder per root element, refused/&lt;root&gt;. */
  static Stream<Fixture> refused() {
    return folders("refused", 1);
  }

  private static Stream<Fixture> folders(String kind, int depth) {
    var base = ROOT.resolve(kind);
    try (var paths = Files.walk(base, depth)) {
      return paths
          .filter(
              p ->
                  !p.equals(base)
                      && base.relativize(p).getNameCount() == depth
                      && Files.isDirectory(p))
          .sorted()
          .map(p -> new Fixture(ROOT.relativize(p).toString(), p))
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path root() {
    try {
      return Path.of(
          Runfiles.preload()
              .withSourceRepository(AutoBazelRepository_Fixtures.NAME)
              .rlocation("kludgeworks_viewer/formex-converter/test/fixtures"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
