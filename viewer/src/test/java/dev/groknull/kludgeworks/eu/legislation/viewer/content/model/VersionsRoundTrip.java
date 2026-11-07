// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Overlay;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes every version of an act into one tree, in order, and rebuilds each version as it was. The
 * versions are legal-text files in the directory {@code LEGAL_TEXT_DIR}. The test also logs what
 * each version added to the tree, to show how much the versions share.
 *
 * <p>Run it by hand, on files converted before. For example, the 49 versions of 02005R0396 are 1.1
 * GB.
 *
 * <pre>
 * bazel test
 * //viewer/src/test/java/dev/groknull/kludgeworks/eu/legislation/viewer/content/model:versions
 * --test_env=LEGAL_TEXT_DIR=…
 * </pre>
 */
class VersionsRoundTrip {
  private static final Logger LOG = LoggerFactory.getLogger(VersionsRoundTrip.class);
  private static final Map<ContentHash, Node> NODES = new LinkedHashMap<>();
  private static final Map<ContentHash, Overlay> OVERLAYS = new LinkedHashMap<>();

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
  void rebuildsEachVersionFromTheSharedTree(Path version) {
    var text = LegalTexts.parse(version);
    var encoded = Encoder.encode(text);
    var nodes = NODES.size();
    var bytes = NODES.values().stream().mapToLong(n -> n.content().length).sum();
    encoded.nodes().forEach(NODES::putIfAbsent);
    encoded.overlays().forEach(OVERLAYS::putIfAbsent);
    var added = NODES.values().stream().mapToLong(n -> n.content().length).sum() - bytes;

    var rebuilt = LegalTexts.rebuild(new Tree(encoded.root(), NODES, OVERLAYS));

    LOG.info(
        "{}: {} KB of XML, {} nodes ({} new, {} KB), {} segments",
        version.getFileName(),
        version.toFile().length() / 1024,
        encoded.nodes().size(),
        NODES.size() - nodes,
        added / 1024,
        Segmentation.of(encoded).size());
    assertThat(LegalTexts.canonical(rebuilt)).isEqualTo(LegalTexts.canonical(text));
  }
}
