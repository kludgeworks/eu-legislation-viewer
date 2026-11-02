// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Versions' texts through the module's API: built in the background on their first read, by ingest
 * on the converter's fixtures (FormexFixtures: 2015/1998's consolidation of 22 March 2026 in
 * English, no legal act in French), and kept as the event says.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class TextsTests {
  @Autowired Acts acts;
  @Autowired Texts texts;
  @Autowired Content content;
  @Autowired JdbcClient jdbc;

  @Test
  void buildsATextOnItsFirstReadAndKeepsIt() throws Exception {
    var version = consolidation();

    assertThat(texts.text(version, Language.ENG)).isInstanceOf(Text.Building.class);

    var text =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(() -> texts.text(version, Language.ENG), Text.Ready.class::isInstance);
    var root = ((Text.Ready) text).root();
    assertThat(
            content.rebuild(
                root, new Processor(false).newDocumentBuilder().newBuildingStreamWriter()))
        .isTrue();
    await().until(() -> kept(version.celex()), kept -> kept == 1);
  }

  @Test
  void saysWhyATextCouldntBeBuilt() {
    var version = consolidation();

    var text =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(() -> texts.text(version, Language.FRA), Text.Failed.class::isInstance);

    assertThat(text).isEqualTo(new Text.Failed(Failure.OUT_OF_SCOPE));
  }

  @Test
  void hasNoTextInALanguageTheVersionIsntIn() {
    assertThat(texts.text(consolidation(), Language.POL)).isEqualTo(new Text.Missing());
  }

  private Version consolidation() {
    return acts.find(OneActCellar.CELEX).orElseThrow().timeline().latest();
  }

  private long kept(String celex) {
    return jdbc.sql("select count(*) from expression where celex = ?")
        .param(celex)
        .query(Long.class)
        .single();
  }
}
