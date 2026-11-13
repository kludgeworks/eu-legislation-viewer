// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ExpressionStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests the texts of versions through the module's API. The tests prepare a whole act. The ingest
 * module builds its versions in the background on the converter's fixtures, {@code FormexFixtures}:
 *
 * <ul>
 *   <li>In English, the consolidations of 2015/1998 of 22 March and 1 October 2026.
 *   <li>No Formex of the other versions.
 *   <li>In French, a document that is not a legal act.
 * </ul>
 *
 * <p>The texts are kept as the events say, in any order the events arrive. When a version and the
 * one before it are both built, they are compared before a reader asks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class TextsTests {
  @Autowired Acts acts;
  @Autowired Texts texts;
  @Autowired ExpressionStore expressions;
  @Autowired Content content;
  @Autowired StepCounts stepCounts;
  @Autowired JdbcClient jdbc;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transaction;

  @Test
  void preparesEveryVersionOfAnActInTheLanguageAtOnceAndKeepsThem() throws Exception {
    var act = act();
    var timeline = act.timeline();
    var version = consolidation();

    var preparing = texts.prepare(act, Language.ENG);
    assertThat(preparing.size()).isEqualTo(5);
    assertThat(preparing.settled()).isFalse();
    assertThat(Objects.requireNonNull(preparing.texts().get(version)).settled()).isFalse();

    // The texts are kept from the events of ingest alone.
    var prepared =
        await()
            .atMost(Duration.ofMinutes(2))
            .until(() -> texts.preparation(act, Language.ENG), Preparation::settled);
    // The two versions in the fixtures are built, with any that other tests kept. The fixtures
    // have no Formex of the original, so it failed.
    assertThat(
            prepared.texts().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Text.Ready)
                .map(entry -> entry.getKey().celex()))
        .contains("02015R1998-20260322", "02015R1998-20261001");
    assertThat(prepared.texts().get(timeline.first()))
        .isInstanceOfSatisfying(
            Text.Failed.class, failed -> assertThat(failed.failure()).isEqualTo(Failure.NO_FORMEX));
    var text = ((Text.Ready) Objects.requireNonNull(prepared.texts().get(version))).text();
    assertThat(content.read(text)).isPresent();
    await().until(() -> kept(version.celex()), kept -> kept == 1);
    // The preparation settles once. It records what was done to each version as it happened.
    assertThat(prepared.took()).isPresent();
    assertThat(prepared.steps())
        .filteredOn(step -> step.celex().equals(version.celex()))
        .extracting(Preparation.Step::kind)
        .containsExactly(Preparation.Step.Kind.STARTED, Preparation.Step.Kind.BUILT);
    assertThat(
            prepared.steps().stream()
                .filter(step -> step.kind() == Preparation.Step.Kind.BUILT)
                .map(Preparation.Step::took))
        .allSatisfy(took -> assertThat(took).isNotNull());
  }

  @Test
  void comparesAVersionWithTheOneBeforeOnceBothAreBuilt() {
    var versions = timeline().versions();
    var earlier = versions.get(1);
    var later = versions.get(2);
    // Any two texts can stand in for the texts of the versions.
    var before =
        content.store(
            earlier.celex(),
            Language.ENG,
            "test",
            LegalTexts.sample("06.02.1/02015R1998-20260101.xml"));
    var after =
        content.store(
            later.celex(),
            Language.ENG,
            "test",
            LegalTexts.sample("06.02.1/02015R1998-20260322.xml"));

    // The later text is built first. It is kept with no text before it. It is compared when the
    // earlier text is kept.
    built(later);
    await().until(() -> kept(later.celex()), kept -> kept == 1);
    built(earlier);

    await()
        .atMost(Duration.ofMinutes(1))
        .until(() -> compared(before, after), compared -> compared == 1);
  }

  @Test
  void saysWhyATextCouldntBeBuilt() {
    var prepared =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(() -> texts.prepare(act(), Language.FRA), Preparation::settled);

    assertThat(prepared.texts().get(consolidation()))
        .isInstanceOfSatisfying(
            Text.Failed.class,
            failed -> assertThat(failed.failure()).isEqualTo(Failure.OUT_OF_SCOPE));
  }

  @Test
  void triesAnActAgainWhereThatMayHelpAndPreparesItOver() {
    var act = act();
    var celex = consolidation().celex();
    await()
        .atMost(Duration.ofMinutes(1))
        .until(() -> texts.prepare(act, Language.FRA), Preparation::settled);
    // This makes it look as if the Publications Office did not answer for the version on any
    // attempt.
    jdbc.sql(
            "update ingest_job set failure = 'UNAVAILABLE' where celex = ? and language_code ="
                + " 'FRA'")
        .param(celex)
        .update();
    jdbc.sql(
            "update expression set failure = 'UNAVAILABLE' where celex = ? and language_code ="
                + " 'FRA'")
        .param(celex)
        .update();

    texts.retry(act, Language.FRA);

    // The act is prepared again. Only the failed version is tried again. It fails as the fixtures
    // do in French.
    var prepared =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(
                () -> texts.preparation(act, Language.FRA),
                preparation ->
                    preparation.settledAt() != null
                        && preparation.steps().stream()
                            .anyMatch(step -> step.kind() == Preparation.Step.Kind.FAILED));
    // The steps are either "started, then failed" or only "failed". The events arrive in any
    // order, and a text that failed does not start after that.
    assertThat(prepared.steps())
        .allSatisfy(step -> assertThat(step.celex()).isEqualTo(celex))
        .extracting(Preparation.Step::kind)
        .isIn(
            List.of(Preparation.Step.Kind.STARTED, Preparation.Step.Kind.FAILED),
            List.of(Preparation.Step.Kind.FAILED));
    assertThat(prepared.texts().get(consolidation()))
        .isInstanceOfSatisfying(
            Text.Failed.class,
            failed -> assertThat(failed.failure()).isEqualTo(Failure.OUT_OF_SCOPE));
  }

  @Test
  void movesATextOnOnlyAsItsEventsArriveInAnyOrder() {
    var celex = timeline().versions().get(1).celex();
    // No other test prepares this language.
    var language = Language.DEU;
    expressions.queue(List.of(celex), language);

    assertThat(expressions.start(celex, language, 1)).isTrue();
    assertThat(expressions.defer(celex, language, 1)).isTrue();
    // The start of the first attempt arrives again, late.
    assertThat(expressions.start(celex, language, 1)).isFalse();
    assertThat(text(celex, language)).isEqualTo(new Text.Queued());
    // The deferral of the second attempt arrives before its start.
    assertThat(expressions.defer(celex, language, 2)).isTrue();
    assertThat(expressions.start(celex, language, 2)).isFalse();
    assertThat(expressions.start(celex, language, 3)).isTrue();
    assertThat(text(celex, language)).isEqualTo(new Text.Building(3));

    var stored =
        content.store(
            celex, language, "test", LegalTexts.sample("06.02.1/02015R1998-20260322.xml"));
    assertThat(expressions.keep(celex, language)).isTrue();
    assertThat(expressions.keep(celex, language)).isFalse();
    assertThat(expressions.defer(celex, language, 3)).isFalse();
    assertThat(expressions.start(celex, language, 4)).isFalse();
    assertThat(expressions.fail(celex, language, Failure.ERROR, null)).isFalse();
    assertThat(text(celex, language)).isEqualTo(new Text.Ready(stored));
  }

  @Test
  void removesTheStepCountsOfATextThatIsReplaced() {
    var act = act().id();
    var replaced =
        content.store(
            "test-replaced",
            Language.ENG,
            "test",
            LegalTexts.sample("06.02.1/02015R1998-20260101.xml"));
    var other =
        content.store(
            "test-other",
            Language.ENG,
            "test",
            LegalTexts.sample("06.02.1/02015R1998-20260322.xml"));
    var counted = new StepCounts.Counted(Map.of(new ActIdentifier("32026R0449"), 6), 0, 0);
    stepCounts.add(act, replaced, other, counted);
    stepCounts.add(act, other, replaced, counted);

    content.store(
        "test-replaced",
        Language.ENG,
        "test",
        LegalTexts.sample("06.02.1/02015R1998-20261001.xml"));

    await()
        .atMost(Duration.ofMinutes(1))
        .until(
            () ->
                stepCounts.find(act, replaced, other).isEmpty()
                    && stepCounts.find(act, other, replaced).isEmpty());
  }

  private Text text(String celex, Language language) {
    var celexes = List.of(celex);
    return Objects.requireNonNull(
        expressions.texts(celexes, language, content.texts(celexes, language)).get(celex));
  }

  private Act act() {
    return acts.find(OneActCellar.CELEX).orElseThrow();
  }

  private Timeline timeline() {
    return act().timeline();
  }

  private Version consolidation() {
    return timeline().startingOn(LocalDate.of(2026, 3, 22)).orElseThrow();
  }

  private void built(Version version) {
    transaction.executeWithoutResult(
        status -> events.publishEvent(new ExpressionBuilt(version.celex(), Language.ENG)));
  }

  private long compared(TextId from, TextId to) {
    return jdbc.sql("select count(*) from text_comparison where from_text = ? and to_text = ?")
        .param(from.value())
        .param(to.value())
        .query(Long.class)
        .single();
  }

  /** How many texts in English of {@code celex} are kept as built. */
  private long kept(String celex) {
    return jdbc.sql(
            "select count(*) from expression where celex = ? and language_code = 'ENG' and state ="
                + " 'BUILT'")
        .param(celex)
        .query(Long.class)
        .single();
  }
}
