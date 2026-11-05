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
import java.util.Objects;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Versions' texts through the module's API: an act prepared whole, its versions built in the
 * background by ingest on the converter's fixtures (FormexFixtures: 2015/1998's consolidations of
 * 22 March and 1 October 2026 in English, no Formex of its other versions, no legal act in French),
 * and kept as the event says; a version and the one before it, both built, compared ahead of a
 * reader.
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
  void preparesEveryVersionOfAnActInTheLanguageAtOnceAndKeepsThem() throws Exception {
    var timeline = timeline();
    var version = consolidation();

    var preparing = texts.prepare(timeline, Language.ENG);
    assertThat(preparing.size()).isEqualTo(5);
    assertThat(preparing.settled()).isFalse();
    assertThat(preparing.texts().get(version)).isInstanceOf(Text.Building.class);

    var prepared =
        await()
            .atMost(Duration.ofMinutes(2))
            .until(() -> texts.prepare(timeline, Language.ENG), Preparation::settled);
    // the fixtures' two built (and any other test kept); the original, of which there are none,
    // failed
    assertThat(
            prepared.texts().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Text.Ready)
                .map(entry -> entry.getKey().celex()))
        .contains("02015R1998-20260322", "02015R1998-20261001");
    assertThat(prepared.texts().get(timeline.first()))
        .isEqualTo(new Text.Failed(Failure.NO_FORMEX));
    var root = ((Text.Ready) Objects.requireNonNull(prepared.texts().get(version))).root();
    assertThat(
            content.rebuild(
                root, new Processor(false).newDocumentBuilder().newBuildingStreamWriter()))
        .isTrue();
    await().until(() -> kept(version.celex()), kept -> kept == 1);
  }

  @Test
  void comparesAVersionWithTheOneBeforeOnceBothAreBuilt() {
    var versions = timeline().versions();
    var earlier = versions.get(1);
    var later = versions.get(2);
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
    var prepared =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(() -> texts.prepare(timeline(), Language.FRA), Preparation::settled);

    assertThat(prepared.texts().get(consolidation()))
        .isEqualTo(new Text.Failed(Failure.OUT_OF_SCOPE));
  }

  private Timeline timeline() {
    return acts.find(OneActCellar.CELEX).orElseThrow().timeline();
  }

  private Version consolidation() {
    return timeline().startingOn(LocalDate.of(2026, 3, 22)).orElseThrow();
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
