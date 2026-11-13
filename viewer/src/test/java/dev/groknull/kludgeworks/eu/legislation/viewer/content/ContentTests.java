// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline.Entry;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests that legal texts are stored through the module's API in Postgres, in a container, and read
 * back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class ContentTests {
  private static final String CELEX = "02015R1998-20260322";
  private static final String SAMPLE = "06.02.1/02015R1998-20260322.xml";
  private static final String NEXT = "06.02.1/02015R1998-20261001.xml";

  @Autowired Content content;
  @Autowired JdbcClient jdbc;
  @Autowired TransactionTemplate transactions;

  @Test
  void storesATextWholeAndReadsItAsTheConverterWroteIt() {
    var text = LegalTexts.sample(SAMPLE);

    var id = content.store(CELEX, Language.ENG, "test", text);

    assertThat(content.read(id)).contains(LegalTexts.element(text));
    assertThat(content.contains(id)).isTrue();
    assertThat(content.texts(List.of(CELEX, "02015R1998-20261001"), Language.ENG))
        .containsExactly(entry(CELEX, id));
  }

  @Test
  void writesNothingForATextStoredAlready() {
    var text = LegalTexts.sample(SAMPLE);
    var id = content.store(CELEX, Language.ENG, "test", text);
    var stored = storedAt(id);

    assertThat(content.store(CELEX, Language.ENG, "other", text)).isEqualTo(id);
    assertThat(storedAt(id)).isEqualTo(stored);
  }

  @Test
  void replacesATextStoredAgainWithAnotherContent() {
    var before = content.store(CELEX, Language.ENG, "test", LegalTexts.sample(SAMPLE));

    var after = content.store(CELEX, Language.ENG, "test", LegalTexts.sample(NEXT));

    assertThat(after).isNotEqualTo(before);
    assertThat(content.read(before)).isEmpty();
    assertThat(content.contains(before)).isFalse();
    assertThat(content.read(after)).contains(LegalTexts.element(LegalTexts.sample(NEXT)));
    assertThat(content.texts(List.of(CELEX), Language.ENG)).containsExactly(entry(CELEX, after));
  }

  @Test
  void storesATextOnceWhenTwoWritersStoreItFirstAtTheSameTime() throws Exception {
    var celex = "02015R1998-20990101";
    var text = LegalTexts.sample(SAMPLE);
    var stored = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var first =
        CompletableFuture.supplyAsync(
            () ->
                transactions.execute(
                    status -> {
                      var id = content.store(celex, Language.ENG, "test", text);
                      stored.countDown();
                      await().until(() -> release.getCount() == 0);
                      return id;
                    }));
    stored.await();
    var second =
        CompletableFuture.supplyAsync(() -> content.store(celex, Language.ENG, "test", text));
    await().until(this::aWriterWaitsForALock);

    release.countDown();

    assertThat(second.get()).isEqualTo(first.get());
    assertThat(content.texts(List.of(celex), Language.ENG))
        .containsExactly(entry(celex, first.get()));
  }

  @Test
  void keepsTheTextsOfEachLanguageApart() {
    var english = content.store(CELEX, Language.ENG, "test", LegalTexts.sample(SAMPLE));
    var french = content.store(CELEX, Language.FRA, "test", LegalTexts.sample(SAMPLE));

    assertThat(french).isNotEqualTo(english);
    assertThat(content.texts(List.of(CELEX), Language.FRA)).containsExactly(entry(CELEX, french));
  }

  @Test
  void keepsTheOutlineOfATextWhenItStoresIt() {
    var text = LegalTexts.sample(SAMPLE);
    var id = content.store(CELEX, Language.ENG, "test", text);

    var outline = content.outline(id).orElseThrow();

    assertThat(outline).isEqualTo(Outlines.of(LegalTexts.element(text)));
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
  void readsNothingWhereNothingIsStored() {
    var none = new TextId(-1);

    assertThat(content.read(none)).isEmpty();
    assertThat(content.outline(none)).isEmpty();
    assertThat(content.contains(none)).isFalse();
  }

  /** The entries and all the entries under them. */
  private static List<Entry> all(List<Entry> entries) {
    return entries.stream()
        .flatMap(entry -> Stream.concat(Stream.of(entry), all(entry.entries()).stream()))
        .toList();
  }

  /** Whether a session of the database waits for a lock that another one holds. */
  private boolean aWriterWaitsForALock() {
    return jdbc.sql("select exists (select from pg_stat_activity where wait_event_type = 'Lock')")
        .query(Boolean.class)
        .single();
  }

  private Instant storedAt(TextId id) {
    return jdbc.sql("select stored_at from text where id = ?")
        .param(id.value())
        .query(Timestamp.class)
        .single()
        .toInstant();
  }
}
