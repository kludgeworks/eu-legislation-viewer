// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"viewer.catalogue.search.enabled=true", "viewer.catalogue.search.start-delay=1d"})
@Import(TestcontainersConfiguration.class)
class SearchIndexTests {
  @Autowired MeterRegistry meters;
  @Autowired SearchRebuild job;
  @Autowired Search search;
  @Autowired JdbcTemplate database;
  @Autowired PlatformTransactionManager transactionManager;
  @MockitoBean Sparql cellar;

  @Test
  void servesOnlyCompleteBuilds() throws Exception {
    when(cellar.select(anyString())).thenReturn(new SparqlResult(List.of(), List.of()));
    var query = Search.Query.of("aviation", Search.Kind.ALL, 0, 1).orElseThrow();
    assertThat(search.matching(query).acts()).isEmpty();
    verify(cellar).select(anyString());

    var calls = new AtomicInteger();
    doAnswer(
            call -> {
              String queryText = call.getArgument(0);
              Sparql.CsvSink sink = call.getArgument(1);
              String csv = answer(queryText);
              if (calls.incrementAndGet() == 1) {
                // A second viewer ticks while the first still owns the claim.
                assertThat(
                        database.queryForObject(
                            """
                            select claimed_at > now() - interval '1 minute' and completed_at is null
                            from catalogue_search_rebuild
                            """,
                            Boolean.class))
                    .isTrue();
                assertThat(
                        database.queryForObject(
                            """
                            select pg_try_advisory_xact_lock('catalogue_search_rebuild'::regclass::oid::bigint)
                            """,
                            Boolean.class))
                    .isFalse();
                job.tick();
              }
              sink.copy(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
              return null;
            })
        .when(cellar)
        .copyCsv(anyString(), any());

    job.tick();
    job.tick();
    assertThat(calls).hasValue(39);
    var first = search.matching(query);
    assertThat(first.more()).isTrue();
    assertThat(first.acts().getFirst().id().celex()).isEqualTo("32016R0679");
    assertThat(search.identified("GDPR")).contains(new ActIdentifier("32016R0679"));
    assertThat(search.identified("679/2016")).isEqualTo(search.identified("2016/679"));
    assertThat(search.identified("2016/679")).isPresent();
    var second = search.matching(Search.Query.of("aviation", Search.Kind.ALL, 1, 1).orElseThrow());
    assertThat(second.more()).isFalse();
    assertThat(second.acts().getFirst().amends())
        .extracting(a -> a.id().celex())
        .containsExactly("32016R0679");
    assertThat(
            search
                .matching(Search.Query.of("aviation", Search.Kind.DIRECTIVE, 0, 5).orElseThrow())
                .acts())
        .hasSize(1);
    assertThat(
            search
                .matching(Search.Query.of("aviaton", Search.Kind.AMENDING, 0, 5).orElseThrow())
                .acts())
        .hasSize(1);

    var citing = Search.Query.of("GDPR adequacy Japan", Search.Kind.DIRECTIVE, 0, 5).orElseThrow();
    assertThat(search.matching(citing).acts())
        .extracting(match -> match.id().celex())
        .containsExactly("32016L0001");
    assertThat(search.identified("GDPR adequacy Japan")).isEmpty();
    var pastEnd = Search.Query.of("aviation", Search.Kind.ALL, 2, 1).orElseThrow();
    assertThat(search.matching(pastEnd).acts()).isEmpty();

    makeRebuildDue();
    var attempts = new AtomicInteger();
    doAnswer(
            call -> {
              if (attempts.incrementAndGet() == 39)
                throw new IllegalStateException("last extract failed");
              Sparql.CsvSink sink = call.getArgument(1);
              sink.copy(
                  new ByteArrayInputStream(
                      answer(call.getArgument(0)).getBytes(StandardCharsets.UTF_8)));
              return null;
            })
        .when(cellar)
        .copyCsv(anyString(), any());
    assertThatThrownBy(job::rebuildNow).isInstanceOf(IllegalStateException.class);
    job.tick();
    assertThat(attempts).hasValue(39);
    assertThat(search.matching(query)).isEqualTo(first);

    assertThat(meters.get("catalogue.search.rebuild").tag("outcome", "published").timer().count())
        .isEqualTo(1);
    assertThat(meters.get("catalogue.search.rebuild").tag("outcome", "skipped").timer().count())
        .isEqualTo(3);
    assertThat(
            meters
                .get("catalogue.search.rebuild")
                .tag("outcome", "failed")
                .tag("error", "IllegalStateException")
                .timer()
                .count())
        .isEqualTo(1);
    assertThat(
            meters
                .get("catalogue.search.match")
                .tags("backend", "cellar", "kind", "all")
                .timer()
                .count())
        .isEqualTo(1);
    assertThat(
            meters
                .get("catalogue.search.match")
                .tags("backend", "postgres", "kind", "directive")
                .timer()
                .count())
        .isPositive();
    assertThat(
            meters
                .get("catalogue.search.match")
                .tags("backend", "postgres", "result", "typo")
                .timer()
                .count())
        .isPositive();
    assertThat(meters.get("catalogue.search.identify").tag("method", "alias").timer().count())
        .isPositive();
    assertThat(meters.get("catalogue.search.identify").tag("method", "citation").timer().count())
        .isPositive();
    assertThat(
            meters
                .get("catalogue.search.extract")
                .tag("family", "amends")
                .tag("error", "IllegalStateException")
                .timer()
                .count())
        .isEqualTo(1);
    assertThat(meters.get("catalogue.search.extract").tag("family", "citations").timer().count())
        .isEqualTo(2);
    verifyShrinkGuard();
  }

  private void verifyShrinkGuard() throws Exception {
    // Force the cooldown to expire, then exercise the guard against a known 100-act index.
    database.update(
        """
        insert into catalogue_search_work (celex,citation_count)
        select '32016D' || lpad(n::text,4,'0'),0 from generate_series(1,98) n
        """);
    var staged = new AtomicInteger();
    doAnswer(
            call -> {
              String queryText = call.getArgument(0);
              String csv = answer(queryText).lines().findFirst().orElseThrow() + "\n";
              if (queryText.contains("3201[5-9]")) {
                var rows = new StringBuilder(csv);
                for (int n = 1; n <= staged.get(); n++) {
                  var celex = "32016D%04d".formatted(n);
                  if (queryText.contains("SELECT ?celex ?eli"))
                    rows.append(celex).append(",,,,true\n");
                  if (queryText.contains("SELECT ?celex ?title"))
                    rows.append(celex).append(",Example\n");
                }
                csv = rows.toString();
              }
              Sparql.CsvSink sink = call.getArgument(1);
              sink.copy(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
              return null;
            })
        .when(cellar)
        .copyCsv(anyString(), any());
    for (int rejected : List.of(0, 94)) {
      staged.set(rejected);
      makeRebuildDue();
      assertThatThrownBy(job::rebuildNow)
          .hasMessageContaining("%d acts staged, 100 previously indexed", rejected);
      assertThat(
              database.queryForObject("select count(*) from catalogue_search_work", Integer.class))
          .isEqualTo(100);
    }
    staged.set(95);
    makeRebuildDue();
    job.tick();
    assertThat(database.queryForObject("select count(*) from catalogue_search_work", Integer.class))
        .isEqualTo(95);

    // The scheduled tick logs a failure itself and does not throw it to the scheduler.
    staged.set(0);
    makeRebuildDue();
    assertThatCode(job::tick).doesNotThrowAnyException();
    assertThat(database.queryForObject("select count(*) from catalogue_search_work", Integer.class))
        .isEqualTo(95);
  }

  private void makeRebuildDue() {
    database.update(
        """
        update catalogue_search_rebuild set claimed_at=now()-interval '3 days',
          completed_at=now()-interval '2 days'
        """);
  }

  @Test
  void expandsHyphenatedAliasOnlyForMatchingLongerQueries() {
    rolledBack(this::expandsHyphenatedAlias);
  }

  private void expandsHyphenatedAlias() {
    database.update("update catalogue_search_rebuild set completed_at=now()");
    database.update(
        """
        insert into catalogue_search_work (celex,citation_count)
        values ('32023R1114',1), ('32024L0002',1)
        """);
    database.update(
        """
        insert into catalogue_search_expression
        values ('32024L0002','ENG','Reporting','Reporting','{}',to_tsvector('english','reporting 2023/1114'))
        """);
    assertThat(search.identified("Markets in Crypto-Assets"))
        .contains(new ActIdentifier("32023R1114"));
    var typed = "markets in crypto-assets reporting";
    assertThat(search.identified(typed)).isEmpty();
    assertThat(search.matching(Search.Query.of(typed, Search.Kind.ALL, 0, 5).orElseThrow()).acts())
        .extracting(match -> match.id().celex())
        .containsExactly("32024L0002");
  }

  @Test
  void listsBothOwnersOfAnAmbiguousNumberBeforeActsThatCiteIt() {
    rolledBack(this::listsBothOwnersOfAnAmbiguousNumber);
  }

  private void listsBothOwnersOfAnAmbiguousNumber() {
    database.update("update catalogue_search_rebuild set completed_at=now()");
    database.update(
        """
        insert into catalogue_search_work (celex,citation_count) values
          ('32010R0185',1),('32010D0185',2),('32015R1998',500)
        """);
    database.update(
        """
        insert into catalogue_search_expression
        select celex,'ENG','Aviation','Aviation','{}',to_tsvector('simple','185/2010')
        from catalogue_search_work where celex in ('32010R0185','32010D0185','32015R1998')
        """);
    assertThat(search.identified("185/2010")).isEmpty();
    var query = Search.Query.of("185/2010", Search.Kind.ALL, 0, 3).orElseThrow();
    assertThat(search.matching(query).acts())
        .extracting(match -> match.id().celex())
        .containsExactly("32010D0185", "32010R0185", "32015R1998");
  }

  /**
   * Runs {@code test} in a transaction that is rolled back, so that the rows that it inserts leave
   * the index as the other tests expect it. The search joins the transaction.
   */
  private void rolledBack(Runnable test) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              status.setRollbackOnly();
              test.run();
            });
  }

  private static String answer(String query) {
    boolean inPartition = query.contains("3201[5-9]");
    if (query.contains("SELECT ?celex ?eli")) {
      return "celex,eli,resource_type,document_date,in_force\n"
          + (inPartition ? "32016R0679,,,,true\n32016L0001,,,,false\n" : "");
    }
    if (query.contains("SELECT ?celex ?title")) {
      return "celex,title\n"
          + (inPartition
              ? "32016R0679,Aviation safety\n"
                  + "32016L0001,Aviation safety and adequacy of Japan pursuant to Regulation (EU)"
                  + " 2016/679\n"
              : "");
    }
    if (query.contains("SELECT ?celex ?amends")) {
      return "celex,amends,amends_eli\n" + (inPartition ? "32016L0001,32016R0679,\n" : "");
    }
    if (query.contains("AS ?citations")) return "celex,citations\n32016R0679,50\n";
    if (query.contains("?concept ?pref_label ?alt_label")) return "concept,pref_label,alt_label\n";
    if (query.contains("?short_title ?alternative_title"))
      return "celex,short_title,alternative_title\n";
    if (query.contains("SELECT ?celex ?concept WHERE")) return "celex,concept\n";
    throw new AssertionError(query);
  }
}
