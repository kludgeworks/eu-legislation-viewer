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
 * Every version of an act in a directory of legal-text files (LEGAL_TEXT_DIR), in order, encoded
 * into one tree and rebuilt as it was; with what each version added to the tree, to see how much
 * versions share. Run by hand, on files converted beforehand (02005R0396's 49 versions: 1.1 GB):
 * bazel test
 * //viewer/src/test/java/dev/groknull/kludgeworks/eu/legislation/viewer/content/model:versions
 * --test_env=LEGAL_TEXT_DIR=…
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
