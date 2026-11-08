// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the notice of {@link ArrivingView} on the pages of 2015/1998 in English while a new version
 * is prepared. The latest version appeared after the act was prepared. The versions are Cellar's,
 * recorded.
 */
class ArrivingViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final ContentHash ROOT = new ContentHash("ab".repeat(32));

  @Test
  void saysWhichVersionAppearedAndThatItIsThereOnTheNextLoad() {
    var latest = AVSEC.timeline().latest();

    var arriving = ArrivingView.of(prepared(new Text.Queued())).orElseThrow();

    var notice =
        "A new version has appeared: "
            + latest.celex()
            + ", applies from "
            + Dates.inWords(latest.date())
            + ". It's being prepared and will be here the next time you load this page.";
    assertThat(arriving.notice()).isEqualTo(notice);
  }

  @Test
  void saysNothingOnceItIsReady() {
    assertThat(ArrivingView.of(prepared(new Text.Ready(ROOT)))).isEmpty();
  }

  /**
   * The act prepared in English. Its latest version has the text {@code latest}. Its other versions
   * are ready.
   */
  private static Preparation prepared(Text latest) {
    var texts = new LinkedHashMap<Version, Text>();
    for (var version : AVSEC.timeline().in(Language.ENG).orElseThrow().versions()) {
      texts.put(version, new Text.Ready(ROOT));
    }
    texts.put(AVSEC.timeline().latest(), latest);
    return new Preparation(
        AVSEC.id(), Language.ENG, Instant.EPOCH, Instant.EPOCH, texts, List.of(), 0);
  }
}
