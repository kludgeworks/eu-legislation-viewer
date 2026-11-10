// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline.Entry;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Chunker;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Tests that legal texts are stored through the module's API in Postgres, in a container, and
 * rebuilt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class ContentTests {
  private static final String SAMPLE = "06.02.1/02015R1998-20260322.xml";
  private static final String NEXT = "06.02.1/02015R1998-20261001.xml";

  @Autowired Content content;
  @Autowired JdbcClient jdbc;

  @Test
  void storesATextWholeAndRebuildsIt() throws Exception {
    var text = LegalTexts.sample(SAMPLE);

    var root = content.store(text);

    var out = LegalTexts.SAXON.newDocumentBuilder().newBuildingStreamWriter();
    assertThat(content.rebuild(root, out)).isTrue();
    assertThat(LegalTexts.lines(LegalTexts.canonical(out.getDocumentNode())))
        .containsExactlyElementsOf(LegalTexts.lines(LegalTexts.canonical(text)));
    assertThat(content.read(root)).isPresent();
  }

  @Test
  void writesOnlyTheChunksThatAreNotStoredYet() {
    content.store(LegalTexts.sample(SAMPLE));
    var stored = chunks();
    var next = Chunker.of(Encoder.encode(LegalTexts.sample(NEXT)));

    content.store(LegalTexts.sample(NEXT));

    assertThat(chunks() - stored).isLessThan(next.manifest().size());
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
  void keepsTheOutlineOfATextWhenItStoresIt() {
    var text = LegalTexts.sample(SAMPLE);
    var root = content.store(text);

    var outline = content.outline(root).orElseThrow();

    assertThat(outline).isEqualTo(Outlines.of(Encoder.encode(text)));
    assertThat(outline.entries())
        .extracting(Entry::eId, Entry::kind, Entry::num)
        .containsExactly(
            tuple("art_1", "article", "Article 1"),
            tuple("art_2", "article", "Article 2"),
            tuple("art_3", "article", "Article 3"),
            tuple("anx", "annex", "ANNEX"));
    var annex = outline.entries().getLast();
    assertThat(annex.entries()).hasSize(12);
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
    assertThat(content.read(new ContentHash("0".repeat(64)))).isEmpty();
    assertThat(content.outline(new ContentHash("0".repeat(64)))).isEmpty();
  }

  /** The entries and all the entries under them. */
  private static List<Entry> all(List<Entry> entries) {
    return entries.stream()
        .flatMap(entry -> Stream.concat(Stream.of(entry), all(entry.entries()).stream()))
        .toList();
  }

  private long chunks() {
    return jdbc.sql("select count(*) from chunk").query(Long.class).single();
  }

  private long rows() {
    return jdbc.sql("select (select count(*) from chunk) + (select count(*) from manifest)")
        .query(Long.class)
        .single();
  }
}
