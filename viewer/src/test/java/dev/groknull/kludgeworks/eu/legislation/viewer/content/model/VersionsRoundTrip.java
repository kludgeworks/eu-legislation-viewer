// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes every version of an act as JSON and reads each back, as the converter wrote it. The
 * versions are legal-text files in the directory {@code LEGAL_TEXT_DIR}. The test also logs the
 * size of the JSON of each version and how long the decoding took.
 *
 * <p>Run it by hand, on files converted before. For example, the 49 versions of 02005R0396 are 1.1
 * GB.
 *
 * <pre>
 * bazel test
 * //viewer/src/test/java/dev/groknull/kludgeworks/eu/legislation/viewer/content:versions
 * --test_env=LEGAL_TEXT_DIR=…
 * </pre>
 */
class VersionsRoundTrip {
  private static final Logger LOG = LoggerFactory.getLogger(VersionsRoundTrip.class);

  static Stream<Path> versions() throws IOException {
    var dir = Path.of(Objects.requireNonNull(System.getenv("LEGAL_TEXT_DIR"), "LEGAL_TEXT_DIR"));
    try (var files = Files.list(dir)) {
      return files
          .filter(f -> f.toString().endsWith(".xml") && !f.toString().endsWith(".in.xml"))
          .sorted()
          .toList()
          .stream();
    }
  }

  @ParameterizedTest
  @MethodSource("versions")
  void decodesEachVersionAsTheConverterWroteIt(Path version) {
    var text = LegalTexts.parse(version);
    var json = Encoder.encode(text);
    var start = System.nanoTime();

    var decoded = Decoder.element(json);

    LOG.info(
        "{}: {} KB of XML, {} KB of JSON, decoded in {} ms",
        version.getFileName(),
        version.toFile().length() / 1024,
        json.length / 1024,
        (System.nanoTime() - start) / 1_000_000);
    assertThat(decoded).isEqualTo(LegalTexts.element(text));
  }
}
