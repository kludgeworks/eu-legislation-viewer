// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cuts every version of an act into chunks, in order, keeps them in one shared store, and rebuilds
 * each version from it as it was. The versions are legal-text files in the directory {@code
 * LEGAL_TEXT_DIR}. The test also logs what each version added to the store, to show how much the
 * versions share.
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
  private static final Map<ContentHash, byte[]> CHUNKS = new HashMap<>();

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
  void rebuildsEachVersionFromTheSharedChunks(Path version) {
    var text = LegalTexts.parse(version);
    var encoded = Encoder.encode(text);
    var chunks = Chunker.of(encoded);
    var added = 0L;
    var addedBytes = 0L;
    for (var chunk : chunks.bodies().entrySet()) {
      if (CHUNKS.putIfAbsent(chunk.getKey(), chunk.getValue()) == null) {
        added++;
        addedBytes += chunk.getValue().length;
      }
    }
    var shared = new LinkedHashMap<ContentHash, byte[]>();
    chunks.manifest().forEach(hash -> shared.put(hash, CHUNKS.get(hash)));

    var rebuilt = LegalTexts.rebuild(new Chunks(chunks.top(), shared));

    LOG.info(
        "{}: {} KB of XML, {} nodes, {} chunks ({} new, {} KB)",
        version.getFileName(),
        version.toFile().length() / 1024,
        encoded.nodes().size(),
        chunks.bodies().size(),
        added,
        addedBytes / 1024);
    assertThat(LegalTexts.canonical(rebuilt)).isEqualTo(LegalTexts.canonical(text));
  }
}
