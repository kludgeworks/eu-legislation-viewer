// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Segmentation;
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
  void rebuildsNothingWhereNothingIsStored() throws Exception {
    var out = LegalTexts.SAXON.newDocumentBuilder().newBuildingStreamWriter();

    assertThat(content.rebuild(new ContentHash("0".repeat(64)), out)).isFalse();
    assertThat(content.segments(new ContentHash("0".repeat(64)))).isEmpty();
  }

  private long rows() {
    return jdbc.sql(
            "select (select count(*) from node) + (select count(*) from edge)"
                + " + (select count(*) from overlay) + (select count(*) from root_segment)")
        .query(Long.class)
        .single();
  }
}
