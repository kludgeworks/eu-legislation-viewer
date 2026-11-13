// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ExpressionRequested;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests that expressions are built in the background, through the module's API, and stored in
 * Postgres in a container. The Formex comes from the converter's fixtures, {@code FormexFixtures}:
 *
 * <ul>
 *   <li>English: the consolidation of 2015/1998 of 22 March 2026, and 2024/2867.
 *   <li>French: a document that is not a legal act.
 *   <li>German: Formex that the converter does not handle.
 *   <li>Swedish: Cellar does not answer.
 *   <li>Other languages: no Formex.
 * </ul>
 *
 * <p>{@code viewer.ingest.max-attempts} is 1, so a job is tried once. A job that cannot be built
 * fails at once.
 *
 * <p>A text whose version the catalogue does not hold is deleted again once it is built, as the
 * text of a withdrawn version. So a test that reads a built text has the catalogue hold its act
 * first, from {@link OneActCellar}.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "viewer.ingest.max-attempts=1")
@Import({
  TestcontainersConfiguration.class,
  FormexFixturesConfiguration.class,
  OneActCellar.class,
  IngestTests.Events.class
})
class IngestTests {
  private static final String CONSOLIDATION = "02015R1998-20260322";

  @Autowired Ingest ingest;
  @Autowired Acts acts;
  @Autowired Content content;
  @Autowired JdbcClient jdbc;
  @Autowired Events events;
  @Autowired ApplicationEventPublisher publisher;
  @Autowired TransactionTemplate transactions;

  @Test
  void buildsAnExpressionsTextAndSaysSo() {
    assertThat(acts.find(OneActCellar.CELEX)).isPresent();
    ingest.schedule(CONSOLIDATION, Language.ENG);

    var status = finished(CONSOLIDATION, Language.ENG);
    assertThat(status.state()).isEqualTo(State.BUILT);
    assertThat(status.attempts()).isEqualTo(1);
    var text = content.texts(List.of(CONSOLIDATION), Language.ENG).get(CONSOLIDATION);
    assertThat(text).isNotNull();
    assertThat(content.read(text)).isPresent();
    assertThat(events.built)
        .filteredOn(built -> built.celex().equals(CONSOLIDATION))
        .singleElement()
        .satisfies(built -> assertThat(built.language()).isEqualTo(Language.ENG));
  }

  @Test
  void failsWhatIsntALegalAct() {
    assertFails(CONSOLIDATION, Language.FRA, Failure.OUT_OF_SCOPE);
  }

  @Test
  void failsAnotherVersionsFormexFiledUnderTheNumber() {
    assertFails(CONSOLIDATION, Language.ITA, Failure.MISFILED);
  }

  @Test
  void failsFormexTheConverterDoesntHandle() {
    assertFails(CONSOLIDATION, Language.DEU, Failure.UNSUPPORTED);
  }

  @Test
  void failsWhereCellarHasNoFormex() {
    assertFails(CONSOLIDATION, Language.GLE, Failure.NO_FORMEX);
  }

  @Test
  void failsAsUnavailableWhereCellarDoesntAnswerOnTheLastAttempt() {
    assertFails(CONSOLIDATION, Language.SWE, Failure.UNAVAILABLE);
  }

  @Test
  void triesAgainOnlyWhatFailedWhereTryingAgainMayHelp() {
    ingest.schedule("32024R2867", Language.SWE);
    finished("32024R2867", Language.SWE);
    ingest.schedule("32024R2867", Language.FRA);
    finished("32024R2867", Language.FRA);

    assertThat(ingest.retry("32024R2867", Language.FRA)).isFalse();
    assertThat(ingest.retry("32024R2867", Language.SWE)).isTrue();

    var status = finished("32024R2867", Language.SWE);
    assertThat(status.failure()).isEqualTo(Failure.UNAVAILABLE);
    // The retry starts again from the first attempt.
    assertThat(status.attempts()).isEqualTo(1);
    assertThat(events.failed)
        .filteredOn(
            failed -> failed.celex().equals("32024R2867") && failed.language() == Language.SWE)
        .hasSize(2);
  }

  @Test
  void queuesAnExpressionOnce() {
    ingest.schedule(CONSOLIDATION, Language.SPA);
    ingest.schedule(CONSOLIDATION, Language.SPA);
    finished(CONSOLIDATION, Language.SPA);

    var status = ingest.schedule(CONSOLIDATION, Language.SPA);

    assertThat(status.state()).isEqualTo(State.FAILED);
    assertThat(status.attempts()).isEqualTo(1);
    assertThat(jobs(CONSOLIDATION, "SPA")).isEqualTo(1);
  }

  /**
   * Tests a job that runs for longer than the takeover time, because its instance is gone. The job
   * starts again when its request is handed out again, and it is built. The event registry hands
   * out a request again when the request is stale.
   */
  @Test
  void takesOverAJobRunningForTooLong() {
    jdbc.sql(
            """
            insert into ingest_job
              (celex, language_code, state, phase, attempts, started_at, created_at, revision)
            values ('32024R2867', 'ENG', 'RUNNING', 'CONVERTING', 1, now() - interval '1 hour',
              now() - interval '1 hour', 1)
            """)
        .update();
    transactions.executeWithoutResult(
        status ->
            publisher.publishEvent(new ExpressionRequested("32024R2867", Language.ENG, null)));

    var status = finished("32024R2867", Language.ENG);

    assertThat(status.state()).isEqualTo(State.BUILT);
    assertThat(status.attempts()).isEqualTo(2);
  }

  @Test
  void hasNoStatusForWhatWasNeverScheduled() {
    assertThat(ingest.status(CONSOLIDATION, Language.POL)).isEmpty();
  }

  private void assertFails(String celex, Language language, Failure failure) {
    ingest.schedule(celex, language);

    var status = finished(celex, language);

    assertThat(status.state()).isEqualTo(State.FAILED);
    assertThat(status.failure()).isEqualTo(failure);
    assertThat(status.attempts()).isEqualTo(1);
    assertThat(events.failed)
        .filteredOn(failed -> failed.celex().equals(celex) && failed.language() == language)
        .singleElement()
        .satisfies(failed -> assertThat(failed.failure()).isEqualTo(failure));
  }

  private IngestStatus finished(String celex, Language language) {
    return await()
        .atMost(Duration.ofMinutes(1))
        .until(
            () -> ingest.status(celex, language).orElseThrow(),
            status -> status.state() == State.BUILT || status.state() == State.FAILED);
  }

  private long jobs(String celex, String language) {
    return jdbc.sql("select count(*) from ingest_job where celex = ? and language_code = ?")
        .params(celex, language)
        .query(Long.class)
        .single();
  }

  /** The module's events, as a listener in another module would have them. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Events {
    final List<ExpressionBuilt> built = new CopyOnWriteArrayList<>();
    final List<ExpressionFailed> failed = new CopyOnWriteArrayList<>();

    @EventListener
    void on(ExpressionBuilt event) {
      built.add(event);
    }

    @EventListener
    void on(ExpressionFailed event) {
      failed.add(event);
    }
  }
}
