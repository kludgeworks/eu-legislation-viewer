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
 * An act read from Cellar again once it is due, through the module's API: 2015/1998 prepared in
 * English on the converter's fixtures, then Cellar listing its consolidation of 1 March 2022 too
 * (OneActCellar.REFRESHED), which the fixtures have no Formex of; its steps' counts, kept, dropped
 * to be counted again. On its own, so no other test sees the act change.
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
    // not due yet: as kept
    acts.find(OneActCellar.CELEX);
    assertThat(cellar.asked).containsOnlyOnce(OneActCellar.CELEX.celex());
    // a step counted by the act as it was
    var counted = new StepCounts.Counted(Map.of(new ActIdentifier("32026R0449"), 6), 0, 0);
    stepCounts.add(OneActCellar.CELEX, FROM, TO, counted);
    assertThat(stepCounts.find(OneActCellar.CELEX, FROM, TO)).contains(counted);

    // a day later, Cellar listing a version more
    cellar.act = OneActCellar.REFRESHED;
    jdbc.sql("update act set checked_at = now() - interval '2 days' where celex = '32015R1998'")
        .update();

    // as kept, at once; read again in the background
    assertThat(acts.find(OneActCellar.CELEX).orElseThrow().timeline().versions()).hasSize(5);
    var refreshed =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(
                () -> acts.find(OneActCellar.CELEX).orElseThrow(),
                each -> each.timeline().versions().size() == 6);
    var march = refreshed.timeline().startingOn(LocalDate.of(2022, 3, 1)).orElseThrow();
    // prepared on its own, the act's pages not waiting for it
    var prepared =
        await()
            .atMost(Duration.ofMinutes(1))
            .until(
                () -> texts.preparation(refreshed, Language.ENG),
                preparation -> {
                  var text = preparation.texts().get(march);
                  return text != null && text.settled();
                });
    assertThat(prepared.texts().get(march)).isEqualTo(new Text.Failed(Failure.NO_FORMEX));
    assertThat(prepared.preparing()).isFalse();
    assertThat(prepared.arriving()).isEmpty();
    assertThat(cellar.asked).filteredOn(OneActCellar.CELEX.celex()::equals).hasSize(2);
    // to be counted again, by the act as it is now
    assertThat(stepCounts.find(OneActCellar.CELEX, FROM, TO)).isEmpty();
  }
}
