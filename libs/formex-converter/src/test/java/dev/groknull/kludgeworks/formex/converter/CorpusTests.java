// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;
import org.xmlunit.assertj3.XmlAssert;

/**
 * The regression test on the whole corpus: every complete manifestation of one language's mirror,
 * made by tools/tasks/corpus/fetch.sh, checked as the fixtures are. Documents out of scope are
 * skipped. The mirror is formex-corpus/{language} at the repository root:
 *
 * <pre>
 * mise run corpus:fetch [--language ENG]
 * mise run corpus:test [--language ENG]
 * </pre>
 *
 * <p>Each manifestation is a test of its own, named by its Cellar ID, which CorpusEngine finds when
 * it is asked for this class: so Bazel shards the corpus, and --test_filter=&lt;Cellar ID&gt; runs
 * one manifestation alone.
 *
 * <p>corpus:test passes the manifest's hash as CORPUS_MANIFEST, so that Bazel caches the result
 * until the converter or the mirror changes.
 */
final class CorpusTests {

  static final Processor SAXON = new Processor(false);
  static final Stylesheet STYLESHEET = new Stylesheet(SAXON);
  static final TextConservation TEXT = new TextConservation(SAXON);
  static final MetadataConservation METADATA = new MetadataConservation(SAXON);
  static final LegalTextRules RULES = new LegalTextRules(SAXON);

  private CorpusTests() {}

  /** The mirror: CORPUS, which corpus:test sets. */
  static Path corpus() {
    var corpus =
        Path.of(
            Optional.ofNullable(System.getenv("CORPUS"))
                .orElseThrow(() -> new IllegalStateException("Set CORPUS: --test_env=CORPUS=…")));
    if (!Files.isRegularFile(corpus.resolve("manifest.tsv"))) {
      throw new IllegalStateException(
          "No corpus at " + corpus + ": run mise run corpus:fetch, then mise run corpus:test");
    }
    return corpus;
  }

  static void check(Manifestation manifestation, String celex) throws Exception {
    XdmNode legalText;
    try {
      legalText = STYLESHEET.transform(manifestation.source(), Optional.of(celex));
    } catch (Stylesheet.Failure e) {
      // not a legal act, or not the document the manifest names (Cellar files another version's
      // Formex under it): nothing the converter can make of it
      if (Stylesheet.OUT_OF_SCOPE.equals(e.code()) || Stylesheet.MISFILED.equals(e.code())) {
        abort(e.getMessage());
      }
      throw e;
    }
    var xml = SAXON.newSerializer().serializeNodeToString(legalText);
    XmlAssert.assertThat(xml)
        .as("%s", manifestation)
        .isValidAgainst(CorpusTests.class.getResource("schema/legal-text.xsd"));
    assertThat(RULES.violations(legalText))
        .as("%s: breaks legal-text.sch", manifestation)
        .isEmpty();
    assertThat(TEXT.missing(manifestation.source(), legalText))
        .as("%s: source text missing from the output", manifestation)
        .isEmpty();
    assertThat(METADATA.missing(manifestation.source(), legalText))
        .as("%s: source metadata missing from the output", manifestation)
        .isEmpty();
  }

  /**
   * The complete manifestations, by Cellar ID in order, from the mirror's manifest (Cellar ID,
   * lastModified, CELEX), with their CELEX numbers: an OJ act needs it. A row without one is left
   * out, as corpus:fetch doesn't mirror it: the viewer fetches a document by its CELEX number.
   */
  static Map<String, String> manifest(Path corpus) throws IOException {
    var celex = new TreeMap<String, String>();
    try (var lines = Files.lines(corpus.resolve("manifest.tsv"), StandardCharsets.UTF_8)) {
      lines
          .map(line -> line.split("\t", -1))
          .filter(f -> f.length == 3 && !f[2].isEmpty())
          .forEach(f -> celex.put(f[0], f[2]));
    }
    return celex;
  }
}
