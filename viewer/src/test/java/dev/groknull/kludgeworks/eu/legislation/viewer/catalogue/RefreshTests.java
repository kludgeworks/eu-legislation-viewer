// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ActChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests, through the module's API, that a held act that Cellar changed is read again, and that each
 * difference has its effect. The test prepares 2015/1998 in English on the converter's fixtures.
 * Then the scan's event, {@code ActChanged}, says that Cellar changed the act:
 *
 * <ol>
 *   <li>Cellar also lists the consolidation of 1 March 2022, as in {@link OneActCellar#REFRESHED}.
 *       It is prepared in each of its languages, English and French, on its own. The fixtures have
 *       no Formex of it, so each build fails. The kept counts of the act's steps are dropped, to be
 *       counted again.
 *   <li>Cellar no longer lists the consolidation of 22 March 2026. The first read notes it as
 *       missing. A second read 30 minutes later withdraws it, and its text is deleted.
 * </ol>
 *
 * <p>This test class runs on its own, so that no other test sees the act change.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class RefreshTests {
  private static final TextId FROM = new TextId(1);
  private static final TextId TO = new TextId(2);
  private static final String MARCH_2026 = "02015R1998-20260322";

  @Autowired Acts acts;
  @Autowired Texts texts;
  @Autowired Content content;
  @Autowired OneActCellar cellar;
  @Autowired StepCounts stepCounts;
  @Autowired JdbcClient jdbc;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transactions;

  @Test
  void readsAChangedActAgainAndActsOnEachDifference() {
    var act = acts.find(OneActCellar.CELEX).orElseThrow();
    await()
        .atMost(Duration.ofMinutes(2))
        .until(() -> texts.prepare(act, Language.ENG), preparation -> !preparation.preparing());
    assertThat(content.texts(List.of(MARCH_2026), Language.ENG)).containsKey(MARCH_2026);
    // A step counted against the act as it was.
    var counted = new StepCounts.Counted(Map.of(new ActIdentifier("32026R0449"), 6), 0, 0);
    stepCounts.add(OneActCellar.CELEX, FROM, TO, counted);

    // Cellar lists one more version.
    cellar.act = OneActCellar.REFRESHED;
    changed();

    var refreshed =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(
                () -> acts.find(OneActCellar.CELEX).orElseThrow(),
                each -> each.timeline().versions().size() == 6);
    var march = refreshed.timeline().startingOn(LocalDate.of(2022, 3, 1)).orElseThrow();
    // The new version is prepared in each of its languages, on its own.
    for (var language : List.of(Language.ENG, Language.FRA)) {
      var prepared =
          await()
              .atMost(Duration.ofMinutes(1))
              .until(
                  () -> texts.preparation(refreshed, language),
                  preparation -> {
                    var text = preparation.texts().get(march);
                    return text != null && text.settled();
                  });
      // Built, and failed: the converter's fixtures have no Formex of it for the version.
      assertThat(prepared.texts().get(march)).isInstanceOf(Text.Failed.class);
    }
    // The step is to be counted again, against the act as it is now.
    assertThat(stepCounts.find(OneActCellar.CELEX, FROM, TO)).isEmpty();

    // Cellar no longer lists the consolidation of 22 March 2026: one read notes it missing.
    cellar.act =
        OneActCellar.with(
            OneActCellar.REFRESHED.timeline().versions().stream()
                .filter(each -> !each.celex().equals(MARCH_2026))
                .toList());
    changed();
    await().atMost(Duration.ofMinutes(1)).until(() -> missing(MARCH_2026));
    assertThat(acts.find(OneActCellar.CELEX).orElseThrow().timeline().versions()).hasSize(6);

    // A read 30 minutes later withdraws it, and its text goes.
    jdbc.sql("update version set missing_since = now() - interval '31 minutes' where celex = ?")
        .param(MARCH_2026)
        .update();
    changed();
    await()
        .atMost(Duration.ofMinutes(1))
        .until(
            () -> acts.find(OneActCellar.CELEX).orElseThrow().timeline().versions(),
            versions -> versions.stream().noneMatch(each -> each.celex().equals(MARCH_2026)));
    await()
        .atMost(Duration.ofMinutes(1))
        .until(() -> content.texts(List.of(MARCH_2026), Language.ENG).isEmpty());
  }

  /** Publishes what the scan publishes once Cellar changed the act, and the act is quiet. */
  private void changed() {
    transactions.executeWithoutResult(
        status -> events.publishEvent(new ActChanged(OneActCellar.CELEX)));
  }

  private boolean missing(String celex) {
    return jdbc.sql("select missing_since is not null from version where celex = ?")
        .param(celex)
        .query(Boolean.class)
        .single();
  }
}
