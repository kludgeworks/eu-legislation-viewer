// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NeighboursBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests comparisons through the module's API. The texts are stored by the content module, and the
 * comparisons are kept in Postgres in a container. The texts are the consolidations of 2015/1998 of
 * 22 March and 1 October 2026.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class ComparisonsTests {
  @Autowired Comparisons comparisons;
  @Autowired Content content;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transaction;
  @Autowired JdbcClient jdbc;

  @Test
  void worksOutAComparisonTheFirstTimeAndKeepsIt() {
    var march = content.store(LegalTexts.sample("06.02.1/02015R1998-20260322.xml"));
    var october = content.store(LegalTexts.sample("06.02.1/02015R1998-20261001.xml"));

    var worked = comparisons.between(march, october).orElseThrow();

    assertThat(worked.differences()).hasSize(6);
    assertThat(kept(march, october)).isOne();
    assertThat(comparisons.between(march, october)).contains(worked);
    assertThat(kept(march, october)).isOne();
  }

  @Test
  void worksOutTheComparisonOfNeighboursBuiltAheadOfAReader() {
    var january = content.store(LegalTexts.sample("06.02.1/02015R1998-20260101.xml"));
    var march = content.store(LegalTexts.sample("06.02.1/02015R1998-20260322.xml"));

    transaction.executeWithoutResult(
        status -> events.publishEvent(new NeighboursBuilt(january, march)));

    await().atMost(Duration.ofMinutes(1)).until(() -> kept(january, march) == 1);
  }

  @Test
  void hasNoComparisonOfATextNotStored() {
    var march = content.store(LegalTexts.sample("06.02.1/02015R1998-20260322.xml"));
    var none = new ContentHash("00".repeat(32));

    assertThat(comparisons.between(march, none)).isEmpty();
    assertThat(kept(march, none)).isZero();
  }

  private long kept(ContentHash from, ContentHash to) {
    return jdbc.sql("select count(*) from comparison where from_root = ? and to_root = ?")
        .param(from.bytes())
        .param(to.bytes())
        .query(Long.class)
        .single();
  }
}
