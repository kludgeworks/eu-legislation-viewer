// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.xml.stream.XMLStreamWriter;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Expressions built in the background, through the module's API: from the converter's fixtures
 * (FormexFixtures: in English 2015/1998's consolidation of 22 March 2026 and 2024/2867; in French
 * no legal act, in German Formex the converter doesn't handle, in the other languages no Formex),
 * stored in Postgres in a container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({
  TestcontainersConfiguration.class,
  FormexFixturesConfiguration.class,
  IngestTests.Events.class
})
class IngestTests {
  private static final String CONSOLIDATION = "02015R1998-20260322";

  @Autowired Ingest ingest;
  @Autowired Content content;
  @Autowired JdbcClient jdbc;
  @Autowired Events events;

  @Test
  void buildsAnExpressionsTextAndSaysSo() throws Exception {
    ingest.schedule(CONSOLIDATION, Language.ENG);

    var status = finished(CONSOLIDATION, Language.ENG);
    assertThat(status.state()).isEqualTo(State.BUILT);
    assertThat(status.attempts()).isEqualTo(1);
    var root = status.root();
    assertThat(root).isNotNull();
    assertThat(content.rebuild(root, xmlSink())).isTrue();
    assertThat(events.built)
        .filteredOn(built -> built.celex().equals(CONSOLIDATION))
        .singleElement()
        .satisfies(
            built -> {
              assertThat(built.language()).isEqualTo(Language.ENG);
              assertThat(built.root()).isEqualTo(root);
            });
  }

  @Test
  void failsWhatIsntALegalAct() {
    assertFails(CONSOLIDATION, Language.FRA, Failure.OUT_OF_SCOPE);
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
  void queuesAnExpressionOnce() {
    ingest.schedule(CONSOLIDATION, Language.SPA);
    ingest.schedule(CONSOLIDATION, Language.SPA);
    finished(CONSOLIDATION, Language.SPA);

    var status = ingest.schedule(CONSOLIDATION, Language.SPA);

    assertThat(status.state()).isEqualTo(State.FAILED);
    assertThat(status.attempts()).isEqualTo(1);
    assertThat(jobs(CONSOLIDATION, "SPA")).isEqualTo(1);
  }

  /** A job whose worker stopped beating (another instance's, gone) is claimed again and built. */
  @Test
  void claimsAJobWhoseLeaseRanOut() {
    jdbc.sql(
            """
            insert into ingest_job
              (celex, language, state, stage, attempts, owner, heartbeat_at, created_at, version)
            values ('32024R2867', 'ENG', 'RUNNING', 'CONVERTING', 1, 'gone',
              now() - interval '1 hour', now(), 1)
            """)
        .update();

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
    return jdbc.sql("select count(*) from ingest_job where celex = ? and language = ?")
        .params(celex, language)
        .query(Long.class)
        .single();
  }

  private static XMLStreamWriter xmlSink() throws SaxonApiException {
    return new Processor(false).newDocumentBuilder().newBuildingStreamWriter();
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
