// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline.Entry;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.application.TreeStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Segmentation;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Legal texts stored, through the module's API, in Postgres (in a container), and rebuilt. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class ContentTests {
  private static final String SAMPLE = "06.02.1/02015R1998-20260322.xml";

  @Autowired Content content;
  @Autowired JdbcClient jdbc;
  @Autowired TreeStore trees;

  @Test
  void storesATextWholeAndRebuildsIt() throws Exception {
    var text = LegalTexts.sample(SAMPLE);

    var root = content.store(text);

    var out = LegalTexts.SAXON.newDocumentBuilder().newBuildingStreamWriter();
    assertThat(content.rebuild(root, out)).isTrue();
    assertThat(LegalTexts.lines(LegalTexts.canonical(out.getDocumentNode())))
        .containsExactlyElementsOf(LegalTexts.lines(LegalTexts.canonical(text)));
    assertThat(content.segments(root)).isEqualTo(Segmentation.of(Encoder.encode(text)));
  }

  @Test
  void writesNothingForATextStoredAlready() {
    var text = LegalTexts.sample(SAMPLE);
    content.store(text);
    var rows = rows();

    content.store(text);

    assertThat(rows()).isEqualTo(rows);
  }

  @Test
  void outlinesATextFromItsContainersAndTheirTitlesOnly() {
    var text = LegalTexts.sample(SAMPLE);
    var root = content.store(text);

    var outline = content.outline(root).orElseThrow();

    assertThat(outline).isEqualTo(Outlines.of(Encoder.encode(text)));
    assertThat(trees.skeleton(root).orElseThrow().nodes().values())
        .filteredOn(node -> node.kind() == Tree.Kind.LEAF)
        .isNotEmpty()
        .allSatisfy(
            leaf ->
                assertThat(new String(leaf.content(), StandardCharsets.UTF_8))
                    .matches("\\[\"(num|heading)\".*"));
    assertThat(outline.entries())
        .extracting(Entry::eId, Entry::kind, Entry::num)
        .containsExactly(
            tuple("art_1", "article", "Article 1"),
            tuple("art_2", "article", "Article 2"),
            tuple("art_3", "article", "Article 3"),
            tuple("anx", "annex", "ANNEX"));
    var annex = outline.entries().getLast();
    assertThat(annex.entries()).hasSize(11);
    var airportSecurity = annex.entries().getFirst();
    assertThat(airportSecurity)
        .extracting(Entry::eId, Entry::num, Entry::heading)
        .containsExactly("anx/pnt_1", "1.", "AIRPORT SECURITY");
    assertThat(airportSecurity.entries().get(1).entries())
        .extracting(Entry::eId, Entry::heading)
        .containsExactly(
            tuple("anx/pnt_1.1.1", "Boundaries"),
            tuple("anx/pnt_1.1.2", "Security restricted areas"),
            tuple("anx/pnt_1.1.3", "Critical parts of security restricted areas"));
    assertThat(all(outline.entries())).hasSize(355);
  }

  @Test
  void rebuildsNothingWhereNothingIsStored() throws Exception {
    var out = LegalTexts.SAXON.newDocumentBuilder().newBuildingStreamWriter();

    assertThat(content.rebuild(new ContentHash("0".repeat(64)), out)).isFalse();
    assertThat(content.segments(new ContentHash("0".repeat(64)))).isEmpty();
    assertThat(content.outline(new ContentHash("0".repeat(64)))).isEmpty();
  }

  /** The entries and all those under them. */
  private static List<Entry> all(List<Entry> entries) {
    return entries.stream()
        .flatMap(entry -> Stream.concat(Stream.of(entry), all(entry.entries()).stream()))
        .toList();
  }

  private long rows() {
    return jdbc.sql(
            "select (select count(*) from node) + (select count(*) from edge)"
                + " + (select count(*) from overlay) + (select count(*) from root_segment)")
        .query(Long.class)
        .single();
  }
}
