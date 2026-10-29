// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.xmlunit.assertj3.XmlAssert;

/**
 * The regression test on the whole corpus: every complete manifestation of one language's mirror,
 * made by mise-tasks/corpus/fetch.sh, checked as the fixtures are. Documents out of scope are
 * skipped. The mirror is outside the workspace:
 *
 * <pre>
 * mise run corpus:test ../formex-corpus/eng
 * </pre>
 *
 * <p>corpus:test passes the manifest's hash as CORPUS_MANIFEST, so that Bazel caches the result
 * until the converter or the mirror changes.
 */
class CorpusTests {

  static final Processor SAXON = new Processor(false);
  static final FormexConverter CONVERTER = new FormexConverter(SAXON);
  static final TextConservation TEXT = new TextConservation(SAXON);
  static final LegalTextRules RULES = new LegalTextRules(SAXON);

  @TestFactory
  Stream<DynamicTest> convertsEveryDocument() throws IOException {
    var corpus =
        Path.of(
            Optional.ofNullable(System.getenv("CORPUS"))
                .orElseThrow(() -> new IllegalStateException("Set CORPUS: --test_env=CORPUS=…")));
    if (!Files.isRegularFile(corpus.resolve("manifest.tsv"))) {
      throw new IllegalStateException(
          "No corpus at " + corpus + ": run mise run corpus:fetch, then mise run corpus:test");
    }
    var celex = manifest(corpus.resolve("manifest.tsv"));
    return celex.keySet().stream()
        .sorted()
        .map(id -> new Fixture(id, corpus.resolve("files").resolve(id)))
        .map(
            fixture ->
                DynamicTest.dynamicTest(
                    fixture.name(), () -> check(fixture, celex.get(fixture.name()))));
  }

  private static void check(Fixture fixture, Optional<String> celex) throws Exception {
    var legalText =
        Optional.of(fixture.source())
            .map(
                source -> {
                  try {
                    return CONVERTER.convert(source, celex);
                  } catch (OutOfScopeException e) {
                    return abort(e.getMessage());
                  }
                })
            .orElseThrow();
    var xml = SAXON.newSerializer().serializeNodeToString(legalText);
    XmlAssert.assertThat(xml)
        .as("%s", fixture)
        .isValidAgainst(
            CorpusTests.class.getResource(
                "/dev/groknull/kludgeworks/eu/legislation/viewer/text/infra/xml/legal-text.xsd"));
    assertThat(RULES.violations(legalText)).as("%s: breaks legal-text.sch", fixture).isEmpty();
    assertThat(TEXT.missing(fixture.source(), legalText))
        .as("%s: source text missing from the output", fixture)
        .isEmpty();
  }

  /**
   * The complete manifestations, by Cellar ID, from the mirror's manifest (Cellar ID, lastModified,
   * CELEX), with their CELEX numbers: an OJ act needs it.
   */
  private static Map<String, Optional<String>> manifest(Path manifest) throws IOException {
    var celex = new HashMap<String, Optional<String>>();
    try (var lines = Files.lines(manifest, StandardCharsets.UTF_8)) {
      lines
          .map(line -> line.split("\t", -1))
          .filter(f -> f.length == 3)
          .forEach(f -> celex.put(f[0], Optional.of(f[2]).filter(c -> !c.isEmpty())));
    }
    return celex;
  }
}
