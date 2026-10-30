// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Cellar's answers, recorded (viewer/src/test/resources/acts), folded into acts. */
class CellarActsTest {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final ActIdentifier REPEALED = new ActIdentifier("32010R0185");

  @Test
  void readsAnActInForceWithItsVersions() {
    var act = act(AVSEC);

    assertThat(act.eli()).isEqualTo(new Eli("reg_impl", 2015, "1998"));
    assertThat(act.type()).isEqualTo("REG_IMPL");
    assertThat(act.dateOfDocument()).isEqualTo(LocalDate.of(2015, 11, 5));
    assertThat(act.inForce()).isTrue();
    assertThat(act.endOfValidity()).isNull();
    var versions = act.timeline().versions();
    assertThat(versions).hasSize(23);
    assertThat(versions.getFirst()).isEqualTo(Version.original(LocalDate.of(2015, 11, 15), AVSEC));
    assertThat(versions.get(1).celex()).isEqualTo("02015R1998-20160229");
    assertThat(versions.getLast().celex()).isEqualTo("02015R1998-20261001");
  }

  @Test
  void theOriginalTextAppliesFromTheEntryIntoForceUntilTheFirstConsolidation() {
    var timeline = act(AVSEC).timeline();

    assertThat(timeline.applicableOn(LocalDate.of(2015, 11, 14))).isEmpty();
    assertThat(timeline.applicableOn(LocalDate.of(2016, 2, 1)).orElseThrow().kind())
        .isEqualTo(Version.Kind.ORIGINAL);
    assertThat(timeline.applicableOn(LocalDate.of(2026, 1, 15)).orElseThrow().celex())
        .isEqualTo("02015R1998-20260101");
  }

  @Test
  void onlyAVersionPublishedWithoutAPdfIsInterim() {
    var versions = act(AVSEC).timeline().versions();

    assertThat(versions.getLast().interim()).isTrue();
    assertThat(versions.subList(0, versions.size() - 1)).noneMatch(Version::interim);
  }

  @Test
  void aVersionIntegratesTheAmendingActsAndCorrigendaCellarListsButNotOtherConsolidations() {
    var versions = act(AVSEC).timeline().versions();

    assertThat(version(versions, "02015R1998-20260322").integrates())
        .containsExactly(new ActIdentifier("32026R0449"), new ActIdentifier("32026R0449R(01)"));
    // Cellar also lists 02010R0358-20160201, another act's consolidation, here
    assertThat(version(versions, "02015R1998-20190201").integrates())
        .containsExactly(new ActIdentifier("32019R0103"));
  }

  @Test
  void aRepealedActKeepsItsLastVersion() {
    var act = act(REPEALED);

    assertThat(act.inForce()).isFalse();
    assertThat(act.endOfValidity()).isEqualTo(LocalDate.of(2015, 11, 14));
    assertThat(act.timeline().versions()).hasSize(17);
    assertThat(act.timeline().versions().getFirst().date()).isEqualTo(LocalDate.of(2010, 3, 25));
    assertThat(act.versionOn(LocalDate.of(2020, 1, 1)).orElseThrow().celex())
        .isEqualTo("02010R0185-20160201");
  }

  @Test
  void anActCellarDoesNotKnowIsNone() {
    var none = new SparqlResult(List.of("eli"), List.of());

    assertThat(CellarActs.act(AVSEC, none, none)).isEmpty();
  }

  @Test
  void identifiesAnActByItsEli() {
    var eli = new Eli("reg_impl", 2015, "1998");

    assertThat(CellarActs.identity(eli, recorded("act-by-eli-reg_impl-2015-1998.json")))
        .contains(AVSEC);
  }

  private static Act act(ActIdentifier id) {
    return CellarActs.act(
            id,
            recorded("act-" + id.celex() + ".json"),
            recorded("versions-" + id.celex() + ".json"))
        .orElseThrow();
  }

  private static Version version(List<Version> versions, String celex) {
    return versions.stream().filter(v -> v.celex().equals(celex)).findFirst().orElseThrow();
  }

  private static SparqlResult recorded(String name) {
    var resource = "/acts/" + name;
    try (var in =
        Objects.requireNonNull(CellarActsTest.class.getResourceAsStream(resource), resource)) {
      return SparqlResult.parse(in.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
