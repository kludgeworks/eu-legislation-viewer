// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

/**
 * Legal texts converted from real Formex: the converter's expected output for its fixtures,
 * formex-converter/src/test/resources/expected/formex/&lt;Formex version&gt;/&lt;CELEX&gt;.xml (the
 * test target's data). Reading them, not converting again, keeps the viewer's tests to the reader:
 * they change only when the converter's output does.
 */
@AutoBazelRepository
final class SampleLegalTexts {

  private static final Path ROOT = root();

  private SampleLegalTexts() {}

  /** Every sample, by its name relative to formex/: 06.02.1/02015R1998-20260322. */
  static Stream<String> names() {
    try (var files = Files.walk(ROOT)) {
      return files
          .filter(f -> f.toString().endsWith(".xml"))
          .map(f -> ROOT.relativize(f).toString().replaceFirst("\\.xml$", ""))
          .sorted()
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** One sample, parsed: 06.02.1/02015R1998-20260322. */
  static XdmNode read(Processor saxon, String name) {
    var file = ROOT.resolve(name + ".xml");
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("no sample " + name);
    }
    try {
      return saxon.newDocumentBuilder().build(new StreamSource(file.toFile()));
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Path root() {
    try {
      return Path.of(
          Runfiles.preload()
              .withSourceRepository(AutoBazelRepository_SampleLegalTexts.NAME)
              .rlocation("kludgeworks_viewer/formex-converter/src/test/resources/expected/formex"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
