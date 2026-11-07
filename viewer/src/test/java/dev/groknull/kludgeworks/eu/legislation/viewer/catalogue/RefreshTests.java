// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Tests, through the module's API, that an act is read from Cellar again when it is due. The test
 * prepares 2015/1998 in English on the converter's fixtures. Then Cellar also lists the
 * consolidation of 1 March 2022, as in {@link OneActCellar#REFRESHED}. The fixtures have no Formex
 * of that consolidation. The kept counts of the act's steps are dropped, to be counted again. This
 * test class runs on its own, so that no other test sees the act change.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class RefreshTests {
  private static final ContentHash FROM = ContentHash.of(new byte[32]);
  private static final ContentHash TO = new ContentHash("01".repeat(32));

  @Autowired Acts acts;
  @Autowired Texts texts;
  @Autowired OneActCellar cellar;
  @Autowired StepCounts stepCounts;
  @Autowired JdbcClient jdbc;

  @Test
  void readsAnActDueAgainAndPreparesTheVersionsThatAppearedOnTheirOwn() {
    var act = acts.find(OneActCellar.CELEX).orElseThrow();
    await()
        .atMost(Duration.ofMinutes(2))
        .until(() -> texts.prepare(act, Language.ENG), preparation -> !preparation.preparing());
    // The act is not due yet, so it comes from the store.
    acts.find(OneActCellar.CELEX);
    assertThat(cellar.asked).containsOnlyOnce(OneActCellar.CELEX.celex());
    // A step counted against the act as it was.
    var counted = new StepCounts.Counted(Map.of(new ActIdentifier("32026R0449"), 6), 0, 0);
    stepCounts.add(OneActCellar.CELEX, FROM, TO, counted);
    assertThat(stepCounts.find(OneActCellar.CELEX, FROM, TO)).contains(counted);

    // A day later, Cellar lists one more version.
    cellar.act = OneActCellar.REFRESHED;
    jdbc.sql("update act set checked_at = now() - interval '2 days' where celex = '32015R1998'")
        .update();

    // The kept act comes back at once. The act is read again in the background.
    assertThat(acts.find(OneActCellar.CELEX).orElseThrow().timeline().versions()).hasSize(5);
    var refreshed =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(
                () -> acts.find(OneActCellar.CELEX).orElseThrow(),
                each -> each.timeline().versions().size() == 6);
    var march = refreshed.timeline().startingOn(LocalDate.of(2022, 3, 1)).orElseThrow();
    // The new version is prepared on its own. The act's pages do not wait for it.
    var prepared =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(
                () -> texts.preparation(refreshed, Language.ENG),
                preparation -> {
                  var text = preparation.texts().get(march);
                  return text != null && text.settled();
                });
    assertThat(prepared.texts().get(march))
        .isInstanceOfSatisfying(
            Text.Failed.class, failed -> assertThat(failed.failure()).isEqualTo(Failure.NO_FORMEX));
    assertThat(prepared.preparing()).isFalse();
    assertThat(prepared.arriving()).isEmpty();
    assertThat(cellar.asked).filteredOn(OneActCellar.CELEX.celex()::equals).hasSize(2);
    // The step is to be counted again, against the act as it is now.
    assertThat(stepCounts.find(OneActCellar.CELEX, FROM, TO)).isEmpty();
  }
}
