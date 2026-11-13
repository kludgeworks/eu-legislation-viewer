// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ChangeStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.PendingChange;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests the store of the scan of Cellar for changes on Postgres, in a container: that one instance
 * claims the scan at a time, that only the latest claim moves the watermark, and that two instances
 * do not take the same due change.
 *
 * <p>This test class runs on its own, because the scan has one row that the tests change.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, OneActCellar.class})
class ChangeStoreTests {
  private static final Instant WATERMARK = Instant.parse("2026-11-12T10:00:00Z");
  private static final Duration HOUR = Duration.ofHours(1);

  @Autowired ChangeStore store;
  @Autowired Acts acts;
  @Autowired JdbcClient jdbc;
  @Autowired TransactionTemplate transactions;

  @BeforeEach
  void noScanClaimed() {
    jdbc.sql("update change_scan set watermark = ?, claimed_at = '-infinity'")
        .param(Timestamp.from(WATERMARK))
        .update();
    jdbc.sql("delete from act_change").update();
  }

  @Test
  void oneInstanceClaimsTheScanInAnInterval() {
    var claim = store.claim(HOUR);

    assertThat(claim).hasValueSatisfying(each -> assertThat(each.watermark()).isEqualTo(WATERMARK));
    assertThat(store.claim(HOUR)).isEmpty();
  }

  @Test
  void anotherInstanceClaimsTheScanOnceTheIntervalIsOver() {
    var first = store.claim(Duration.ZERO).orElseThrow();

    var second = store.claim(Duration.ZERO);

    assertThat(second)
        .hasValueSatisfying(each -> assertThat(each.claimedAt()).isAfter(first.claimedAt()));
  }

  @Test
  void onlyTheLatestClaimMovesTheWatermark() {
    var first = store.claim(Duration.ZERO).orElseThrow();
    var second = store.claim(Duration.ZERO).orElseThrow();

    assertThat(store.advance(first, WATERMARK.plus(HOUR))).isFalse();
    assertThat(store.advance(second, WATERMARK.plus(HOUR))).isTrue();
    assertThat(watermark()).isEqualTo(WATERMARK.plus(HOUR));
  }

  @Test
  void theWatermarkNeverMovesBack() {
    var claim = store.claim(Duration.ZERO).orElseThrow();

    assertThat(store.advance(claim, WATERMARK.minus(HOUR))).isTrue();
    assertThat(watermark()).isEqualTo(WATERMARK);
  }

  @Test
  void keepsAPendingChange() {
    var act = acts.find(OneActCellar.CELEX).orElseThrow().id();
    var change = new PendingChange(act, WATERMARK, WATERMARK, WATERMARK.plus(HOUR), null);

    transactions.executeWithoutResult(status -> store.save(List.of(change)));

    Map<ActIdentifier, PendingChange> pending =
        transactions.execute(status -> store.pending(List.of(act)));
    assertThat(pending).containsExactlyEntriesOf(Map.of(act, change));
  }

  @Test
  void twoInstancesDoNotTakeTheSameDueChange() throws Exception {
    var act = acts.find(OneActCellar.CELEX).orElseThrow().id();
    var change = new PendingChange(act, WATERMARK, WATERMARK, WATERMARK, null);
    transactions.executeWithoutResult(status -> store.save(List.of(change)));
    var taken = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var first =
        CompletableFuture.supplyAsync(
            () ->
                transactions.execute(
                    status -> {
                      var due = store.due(WATERMARK.plus(HOUR));
                      taken.countDown();
                      awaitQuietly(release);
                      return due;
                    }));
    taken.await();

    var second = transactions.execute(status -> store.due(WATERMARK.plus(HOUR)));
    release.countDown();

    assertThat(first.get()).containsExactly(change);
    assertThat(second).isEmpty();
  }

  private Instant watermark() {
    return jdbc.sql("select watermark from change_scan")
        .query(Timestamp.class)
        .single()
        .toInstant();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
