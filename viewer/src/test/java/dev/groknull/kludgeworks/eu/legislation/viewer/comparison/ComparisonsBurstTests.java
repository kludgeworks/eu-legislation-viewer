// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ConsecutiveTextsBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests that comparisons asked for all at once are all worked out, with fewer connections than
 * comparisons. An act whose texts are all converted already asks for the comparison of each pair of
 * consecutive texts at the same time. The pool has 2 connections, and gives up on a connection
 * after 5 seconds.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.hikari.maximum-pool-size=2",
      "spring.datasource.hikari.connection-timeout=5000"
    })
@Import(TestcontainersConfiguration.class)
class ComparisonsBurstTests {
  @Autowired Content content;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transaction;
  @Autowired JdbcClient jdbc;

  @Test
  void worksOutEveryComparisonAskedForAtOnceWithFewerConnections() {
    var texts =
        List.of(
            store("02015R1998-20260101"),
            store("02015R1998-20260322"),
            store("02015R1998-20261001"));
    var pairs =
        texts.stream()
            .flatMap(
                from -> texts.stream().filter(to -> !to.equals(from)).map(to -> List.of(from, to)))
            .toList();

    transaction.executeWithoutResult(status ->
        pairs.forEach(
            pair -> events.publishEvent(new ConsecutiveTextsBuilt(pair.get(0), pair.get(1)))));

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> pairs.stream().allMatch(pair -> kept(pair.get(0), pair.get(1)) == 1));
    assertThat(
            jdbc.sql("select count(*) from event_publication where completion_date is null")
                .query(Long.class)
                .single())
        .isZero();
  }

  /** Stores the sample {@code celex} of 2015/1998 in English, under its CELEX. */
  private TextId store(String celex) {
    return content.store(
        celex, Language.ENG, "test", LegalTexts.sample("06.02.1/" + celex + ".xml"));
  }

  private long kept(TextId from, TextId to) {
    return jdbc.sql("select count(*) from text_comparison where from_text = ? and to_text = ?")
        .param(from.value())
        .param(to.value())
        .query(Long.class)
        .single();
  }
}
