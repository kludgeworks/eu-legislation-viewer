// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.LocalDate;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Versions' texts through the module's API: built in the background on their first read, by ingest
 * on the converter's fixtures (FormexFixtures: 2015/1998's consolidation of 22 March 2026 in
 * English, no legal act in French), and kept as the event says; a version and the one before it,
 * both built, compared ahead of a reader.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class TextsTests {
  @Autowired Acts acts;
  @Autowired Texts texts;
  @Autowired Content content;
  @Autowired JdbcClient jdbc;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transaction;

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
  void comparesAVersionWithTheOneBeforeOnceBothAreBuilt() {
    var timeline = acts.find(OneActCellar.CELEX).orElseThrow().timeline().versions();
    var earlier = timeline.get(1);
    var later = timeline.get(2);
    // any two texts will do, as the versions' own
    var before = content.store(LegalTexts.sample("06.02.1/02015R1998-20260101.xml"));
    var after = content.store(LegalTexts.sample("06.02.1/02015R1998-20260322.xml"));

    // the later first: kept with no text before it, it's compared once the earlier is
    built(later, after);
    await().until(() -> kept(later.celex()), kept -> kept == 1);
    built(earlier, before);

    await()
        .atMost(Duration.ofMinutes(1))
        .until(() -> compared(before, after), compared -> compared == 1);
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
    return acts.find(OneActCellar.CELEX)
        .orElseThrow()
        .timeline()
        .startingOn(LocalDate.of(2026, 3, 22))
        .orElseThrow();
  }

  private void built(Version version, ContentHash root) {
    transaction.executeWithoutResult(
        status ->
            events.publishEvent(new ExpressionBuilt(version.celex(), Language.ENG, root, "test")));
  }

  private long compared(ContentHash from, ContentHash to) {
    return jdbc.sql("select count(*) from comparison where from_root = ? and to_root = ?")
        .param(from.bytes())
        .param(to.bytes())
        .query(Long.class)
        .single();
  }

  private long kept(String celex) {
    return jdbc.sql("select count(*) from expression where celex = ?")
        .param(celex)
        .query(Long.class)
        .single();
  }
}
