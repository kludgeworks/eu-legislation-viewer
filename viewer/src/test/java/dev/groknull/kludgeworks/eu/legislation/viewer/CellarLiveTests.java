// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.EId;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Unit;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.PointInTime;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The whole chain against the real Cellar, for when it or our queries change: an act by its ELI,
 * its timeline, and the text of the version that applies on a date, fetched, converted and read. On
 * Valkey in a container, as the other whole-application tests. Manual (bazel test
 * //viewer/src/test/java/…/viewer:live): it needs the network and asks Cellar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
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
  void findsActsByTheElisOfActsSharingANumberAndOfTheOjCSeries() {
    var shared = acts.find(new Eli("dec", 2007, "44(1)")).orElseThrow();
    var ojC = acts.find(new Eli("C", 2023, "1464")).orElseThrow();

    assertThat(shared.id()).isEqualTo(new ActIdentifier("32006D0023(01)"));
    assertThat(shared.eli()).isEqualTo(new Eli("dec", 2007, "44(1)"));
    assertThat(ojC.id()).isEqualTo(new ActIdentifier("32023D01464"));
    assertThat(ojC.eli()).isEqualTo(new Eli("C", 2023, "1464"));
  }

  @Test
  void findsATreatyByItsEliWithItsConsolidationsAsVersions() {
    var tfeu = acts.find(Eli.treaty("tfeu_2016")).orElseThrow();

    assertThat(tfeu.id()).isEqualTo(new ActIdentifier("12016E/TXT"));
    assertThat(tfeu.timeline().versions())
        .filteredOn(v -> v.kind() == Version.Kind.CONSOLIDATED)
        .extracting(Version::date)
        .startsWith(LocalDate.of(2015, 12, 25), LocalDate.of(2016, 9, 1));
  }

  @Test
  void readsTheTextOfTheVersionThatAppliesOnADate() {
    var text = textOn(AVSEC, LocalDate.of(2026, 4, 1));

    assertThat(text.act()).isEqualTo(AVSEC);
    assertThat(text.kind()).isEqualTo(Expression.Kind.CONSOLIDATED);
    assertThat(text.version()).isEqualTo(LocalDate.of(2026, 3, 22));
    assertThat(text.body()).isNotEmpty();
  }

  @Test
  void readsTheTextOfATreatyWithItsProtocols() {
    var tfeu = new ActIdentifier("12016E/TXT");
    var text = textOn(tfeu, LocalDate.of(2026, 4, 1));

    assertThat(text.act()).isEqualTo(tfeu);
    assertThat(text.version()).isEqualTo(LocalDate.of(2025, 3, 15));
    assertThat(eIds(text.body())).contains(new EId("art_1"));
    assertThat(eIds(text.annexes())).contains(new EId("pro_3"), new EId("pro_3/art_1"));
  }

  private Expression textOn(ActIdentifier id, LocalDate date) {
    var act = acts.find(id).orElseThrow();
    return pointInTime
        .expression(act, act.versionOn(date).orElseThrow(), Language.ENG)
        .join()
        .orElseThrow();
  }

  private static Stream<EId> eIds(List<Block> blocks) {
    return blocks.stream()
        .flatMap(
            b ->
                b instanceof Unit u
                    ? Stream.concat(Stream.of(u.eId()), eIds(u.content()))
                    : Stream.empty());
  }
}
