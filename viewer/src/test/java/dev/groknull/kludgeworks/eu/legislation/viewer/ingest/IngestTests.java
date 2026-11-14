// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ConversionRequested.Reason;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ConversionQueued;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
  @Autowired Tracer tracer;
  @Autowired ObservationRegistry observations;

  @Test
  void buildsAnExpressionsTextAndSaysSo() {
    assertThat(acts.find(OneActCellar.CELEX)).isPresent();
    request(CONSOLIDATION, Language.ENG, Reason.NEEDED);

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
    request("32024R2867", Language.SWE, Reason.NEEDED);
    finished("32024R2867", Language.SWE);
    request("32024R2867", Language.FRA, Reason.NEEDED);
    finished("32024R2867", Language.FRA);

    request("32024R2867", Language.FRA, Reason.RETRIED);
    request("32024R2867", Language.SWE, Reason.RETRIED);

    // Both are told to have failed a second time: the Swedish after another attempt, the French
    // as it stood.
    await().until(() -> failures("32024R2867", Language.SWE), failures -> failures == 2);
    await().until(() -> failures("32024R2867", Language.FRA), failures -> failures == 2);
    assertThat(starts("32024R2867", Language.SWE)).isEqualTo(2);
    assertThat(starts("32024R2867", Language.FRA)).isEqualTo(1);
    var status = finished("32024R2867", Language.SWE);
    assertThat(status.failure()).isEqualTo(Failure.UNAVAILABLE);
    // The retry starts again from the first attempt.
    assertThat(status.attempts()).isEqualTo(1);
  }

  @Test
  void convertsAgainWhatCellarRepublished() {
    request(CONSOLIDATION, Language.POL, Reason.NEEDED);
    finished(CONSOLIDATION, Language.POL);

    request(CONSOLIDATION, Language.POL, Reason.REPUBLISHED);

    await().until(() -> failures(CONSOLIDATION, Language.POL), failures -> failures == 2);
    assertThat(starts(CONSOLIDATION, Language.POL)).isEqualTo(2);
  }

  @Test
  void queuesAnExpressionOnce() {
    request(CONSOLIDATION, Language.SPA, Reason.NEEDED);
    request(CONSOLIDATION, Language.SPA, Reason.NEEDED);
    finished(CONSOLIDATION, Language.SPA);

    request(CONSOLIDATION, Language.SPA, Reason.NEEDED);

    // It is not converted again: ingest tells again that it failed.
    await().until(() -> failures(CONSOLIDATION, Language.SPA), failures -> failures >= 2);
    assertThat(starts(CONSOLIDATION, Language.SPA)).isEqualTo(1);
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
              (celex, language_code, state, attempts, started_at, created_at, revision)
            values ('32024R2867', 'ENG', 'RUNNING', 1, now() - interval '1 hour',
              now() - interval '1 hour', 1)
            """)
        .update();
    transactions.executeWithoutResult(
        status -> publisher.publishEvent(new ConversionQueued("32024R2867", Language.ENG, null)));

    var status = finished("32024R2867", Language.ENG);

    assertThat(status.state()).isEqualTo(State.BUILT);
    assertThat(status.attempts()).isEqualTo(2);
  }

  @Test
  void continuesTheTraceOfWhoeverAsked() {
    // An observation, as a web request is: its trace goes with the request to ingest's thread.
    var trace =
        Observation.createNotStarted("asking", observations)
            .observe(
                () -> {
                  request(CONSOLIDATION, Language.DAN, Reason.NEEDED);
                  return Objects.requireNonNull(tracer.currentSpan()).context().traceId();
                });

    finished(CONSOLIDATION, Language.DAN);

    assertThat(
            jdbc.sql("select traceparent from ingest_job where celex = ? and language_code = 'DAN'")
                .param(CONSOLIDATION)
                .query(String.class)
                .single())
        .contains(trace);
  }

  @Test
  void hasNoStatusForWhatWasNeverAskedFor() {
    assertThat(ingest.status(CONSOLIDATION, Language.SLK)).isEmpty();
  }

  private void assertFails(String celex, Language language, Failure failure) {
    request(celex, language, Reason.NEEDED);

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
            () ->
                ingest
                    .status(celex, language)
                    .filter(
                        status -> status.state() == State.BUILT || status.state() == State.FAILED),
            Optional::isPresent)
        .orElseThrow();
  }

  /** Asks for the text, as another module does: in a transaction, which the registry needs. */
  private void request(String celex, Language language, Reason reason) {
    transactions.executeWithoutResult(
        status -> publisher.publishEvent(new ConversionRequested(celex, language, reason)));
  }

  private long failures(String celex, Language language) {
    return events.failed.stream()
        .filter(failed -> failed.celex().equals(celex) && failed.language() == language)
        .count();
  }

  private long starts(String celex, Language language) {
    return events.started.stream()
        .filter(started -> started.celex().equals(celex) && started.language() == language)
        .count();
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
    final List<TextConverted> built = new CopyOnWriteArrayList<>();
    final List<ConversionFailed> failed = new CopyOnWriteArrayList<>();
    final List<ConversionStarted> started = new CopyOnWriteArrayList<>();

    @EventListener
    void on(TextConverted event) {
      built.add(event);
    }

    @EventListener
    void on(ConversionFailed event) {
      failed.add(event);
    }

    @EventListener
    void on(ConversionStarted event) {
      started.add(event);
    }
  }
}
