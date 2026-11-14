// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView.Progress;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

/**
 * Tests what the preparing page says while the act waits behind the versions of other acts, and of
 * the versions of 2015/1998 that are worked on. The versions are Cellar's, recorded.
 */
class PreparingViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));

  @Test
  void listsTheVersionsBeingConvertedAndThoseToBeTriedAgain() {
    var versions = AVSEC.timeline().in(Language.ENG).orElseThrow().versions();
    var texts = new LinkedHashMap<Version, Text>();
    versions.forEach(version -> texts.put(version, new Text.Ready(new TextId(1))));
    texts.put(versions.get(0), new Text.Converting(1));
    texts.put(versions.get(1), new Text.Waiting(2));
    texts.put(versions.get(2), new Text.Waiting(0));

    var progress = Progress.of(AVSEC, prepared(texts), Instant.EPOCH);

    assertThat(progress.waiting()).isNull();
    assertThat(progress.running()).hasSize(2);
    assertThat(progress.running().get(0))
        .startsWith("Version 1 of " + versions.size())
        .endsWith(" · converting");
    assertThat(progress.running().get(1))
        .startsWith("Version 2 of " + versions.size())
        .endsWith(" · attempt 2 didn't work; trying again shortly");
  }

  @Test
  void saysTheActWaitsItsTurnOnlyWhileNothingOfItWasTried() {
    var versions = AVSEC.timeline().in(Language.ENG).orElseThrow().versions();
    var texts = new LinkedHashMap<Version, Text>();
    versions.forEach(version -> texts.put(version, new Text.Waiting(0)));

    assertThat(Progress.of(AVSEC, prepared(texts), Instant.EPOCH).waiting()).isNotNull();

    texts.put(versions.get(0), new Text.Waiting(1));

    assertThat(Progress.of(AVSEC, prepared(texts), Instant.EPOCH).waiting()).isNull();
  }

  private static Preparation prepared(SequencedMap<Version, Text> texts) {
    return new Preparation(AVSEC.id(), Language.ENG, Instant.EPOCH, null, texts, List.of(), 0);
  }

  @Test
  void countsTheDatesOfTheAmendedProvisions() {
    assertThat(Progress.dates(386)).isEqualTo("386 dates, one for each amended provision");
    assertThat(Progress.dates(1)).isEqualTo("1 date, for the one amended provision");
    assertThat(Progress.dates(0)).isEqualTo("None: no act has amended its provisions");
  }

  @Test
  void saysPlainlyHowManyVersionsOfOtherActsAreAheadRoundingPastTen() {
    assertThat(Progress.waiting(43))
        .isEqualTo(
            "About 40 versions of other acts are ahead. Nothing's wrong; this one starts by"
                + " itself.");
    assertThat(Progress.waiting(3)).startsWith("3 versions of other acts are ahead.");
    assertThat(Progress.waiting(1)).startsWith("1 version of another act is ahead.");
    assertThat(Progress.waiting(0)).isEqualTo("Nothing's wrong; this one starts by itself.");
  }
}
