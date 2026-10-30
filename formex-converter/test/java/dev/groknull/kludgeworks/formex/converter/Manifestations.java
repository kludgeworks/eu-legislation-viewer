// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The fixtures, the manifestations checked in under formex-converter/test/fixtures (the test
 * target's data), as argument sources for parameterized tests: raw Formex as Cellar serves it.
 */
@AutoBazelRepository
final class Manifestations {

  private static final Path ROOT = root();

  private Manifestations() {}

  /**
   * The fixtures in scope: one folder per manifestation under its Formex version,
   * formex/&lt;version&gt;/&lt;CELEX&gt;.
   */
  static Stream<Manifestation> fixtures() {
    return folders("formex", 2);
  }

  /** One fixture by its folder: formex/06.02.1/02015R1998-20260322. */
  static Manifestation named(String name) {
    var folder = ROOT.resolve(name);
    if (!Files.isDirectory(folder)) {
      throw new IllegalArgumentException("no fixture " + name);
    }
    return new Manifestation(name, folder);
  }

  /** The fixtures out of scope: one folder per root element, refused/&lt;root&gt;. */
  static Stream<Manifestation> refused() {
    return folders("refused", 1);
  }

  private static Stream<Manifestation> folders(String kind, int depth) {
    var base = ROOT.resolve(kind);
    try (var paths = Files.walk(base, depth)) {
      return paths
          .filter(
              p ->
                  !p.equals(base)
                      && base.relativize(p).getNameCount() == depth
                      && Files.isDirectory(p))
          .sorted()
          .map(p -> new Manifestation(ROOT.relativize(p).toString(), p))
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
              .withSourceRepository(AutoBazelRepository_Manifestations.NAME)
              .rlocation("kludgeworks_viewer/formex-converter/test/fixtures"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
