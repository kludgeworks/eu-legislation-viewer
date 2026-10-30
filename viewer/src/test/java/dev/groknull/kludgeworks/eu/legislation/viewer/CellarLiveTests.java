// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.application.PointInTime;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The whole chain against the real Cellar, for when it or our queries change: an act by its ELI,
 * its timeline, and the text of the version that applies on a date, fetched, converted and read.
 * Manual (bazel test //viewer/src/test/java/…/viewer:live): it needs the network and asks Cellar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CellarLiveTests {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");

  @Autowired Acts acts;
  @Autowired PointInTime pointInTime;

  @Test
  void findsAnActByItsEliWithItsVersions() {
    var act = acts.find(new Eli("reg_impl", 2015, "1998")).orElseThrow();

    assertThat(act.id()).isEqualTo(AVSEC);
    assertThat(act.timeline().versions()).hasSizeGreaterThan(20);
  }

  @Test
  void readsTheTextOfTheVersionThatAppliesOnADate() {
    var text = pointInTime.text(AVSEC, LocalDate.of(2026, 4, 1), Language.ENG).join().orElseThrow();

    assertThat(text.act()).isEqualTo(AVSEC);
    assertThat(text.kind()).isEqualTo(LegalText.Kind.CONSOLIDATED);
    assertThat(text.version()).isEqualTo(LocalDate.of(2026, 3, 22));
    assertThat(text.body()).isNotEmpty();
  }
}
