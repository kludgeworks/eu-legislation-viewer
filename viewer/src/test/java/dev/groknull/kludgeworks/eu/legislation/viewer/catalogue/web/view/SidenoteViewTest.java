// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView.Link;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Who made a difference in 2015/1998, as its sidenote says it (Cellar's versions and dates,
 * recorded), from the markers the converter keeps on its units in 2015/1998's consolidations.
 */
class SidenoteViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));

  @Test
  void namesTheActBetweenTheVersionsAndTheStagesItAppliesIn() {
    var c =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Marker("32026R0449", "replaced", null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC, version("02015R1998-20260322"), version("02015R1998-20261001"), c, Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2026/449");
    var applies = sidenote.dates().getFirst();
    // which of its dates this point applies from isn't in the texts
    assertThat(applies.when())
        .isEqualTo(
            "2026/449 applies in stages: from 22 March 2026, its entry into force; in part from"
                + " 1 October 2026");
    assertThat(applies.clause())
        .isEqualTo(
            new Link(
                "Article 2 of 2026/449",
                "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32026R0449"));
    assertThat(applies.after()).isEqualTo(" says which part from when");
    assertThat(sidenote.before()).isEqualTo("Before: original act");
  }

  @Test
  void datesAChangeFromTheActsOneDateOfApplication() {
    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20160229"),
            version("02015R1998-20170607"),
            changed(
                "anx/pnt_1.0.3", List.of(new Marker("32017R0815", "replaced", null)), List.of()),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2017/815");
    assertThat(sidenote.dates())
        .singleElement()
        .satisfies(
            applies -> {
              assertThat(applies.when()).isEqualTo("applies from 1 June 2017");
              assertThat(applies.between()).isEqualTo(" · ");
              assertThat(applies.clause())
                  .extracting(Link::label)
                  .isEqualTo("Article 2 of 2017/815");
              assertThat(applies.after()).isEmpty();
            });
  }

  @Test
  void datesAnActThatOnlyEntersIntoForceFromThatWithTheActThatMovedItsDate() {
    var inserted =
        new Difference(
            "anx/pnt_1.0.6",
            Kind.INSERTED,
            unit("anx/pnt_1.0.6"),
            0,
            0,
            null,
            null,
            null,
            List.of(),
            List.of(new Marker("32019R1583", "inserted", null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            inserted,
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Inserted by 2019/1583");
    var applies = sidenote.dates().getFirst();
    assertThat(applies.when()).isEqualTo("applies from 31 December 2021, its entry into force");
    assertThat(applies.amendedBy())
        .containsExactly(
            new Link(
                "2020/910",
                "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32020R0910"));
    assertThat(sidenote.before()).isNull();
  }

  @Test
  void namesTheActsThatChangedAPointInOneVersionTogether() {
    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            changed(
                "anx/pnt_11.1.2",
                List.of(
                    new Marker("32019R0103", "replaced", "anx/pnt_11.1"),
                    new Marker("32019R1583", "replaced", null)),
                List.of()),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2019/103 and 2019/1583 together");
    assertThat(sidenote.dates()).hasSize(2);
  }

  @Test
  void pointsToTheClauseWhereCellarHasNoDayForTheActsApplication() {
    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20200702"),
            version("02015R1998-20210315"),
            changed(
                "anx/pnt_1.1.3.4", List.of(new Marker("32019R0413", "replaced", null)), List.of()),
            Language.ENG);

    var applies = sidenote.dates().getFirst();
    // its date of application in Cellar is a placeholder; it enters into force on 16 March 2019
    assertThat(
            applies.when() + applies.between() + Objects.requireNonNull(applies.clause()).label())
        .isEqualTo("applies from a date set in Article 3 of 2019/413");
  }

  @Test
  void claimsNoActOutsideTheVersionsAndSaysWhatTheUnitWasBefore() {
    var f =
        changed(
            "anx/pnt_8.1.2.3/pnt_f",
            List.of(new Marker("32023R0566", "replaced", null)),
            List.of(new Marker("32023R0566", "replaced", null)));

    var sidenote =
        SidenoteView.of(
            AVSEC, version("02015R1998-20260322"), version("02015R1998-20261001"), f, Language.ENG);

    assertThat(sidenote.made()).isNull();
    assertThat(sidenote.dates()).isEmpty();
    assertThat(sidenote.before()).isEqualTo("Before: as replaced by 2023/566");
  }

  private static Difference changed(String eId, List<Marker> markers, List<Marker> earlier) {
    return new Difference(
        eId, Kind.CHANGED, unit(eId), 0, 0, null, null, null, List.of(), markers, earlier);
  }

  private static Element unit(String eId) {
    return new Element("unit", Map.of("eId", eId), List.of(), "");
  }

  private static Version version(String celex) {
    return AVSEC.timeline().versions().stream()
        .filter(v -> v.celex().equals(celex))
        .findFirst()
        .orElseThrow();
  }
}
