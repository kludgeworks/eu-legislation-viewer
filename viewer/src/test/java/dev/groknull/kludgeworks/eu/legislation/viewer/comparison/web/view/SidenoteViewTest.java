// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView.Link;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Tests how the sidenote of {@link SidenoteView} says who made a difference in 2015/1998. The
 * sidenote uses two sources. The first is the markers that the converter keeps on the units of the
 * consolidations of 2015/1998. The second is Cellar's amendments of those units. The versions,
 * dates and amendments are Cellar's, recorded.
 */
class SidenoteViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));

  @Test
  void namesTheActBetweenTheVersionsAndTheStagesItAppliesInWhereCellarDoesntSayWhich() {
    var c =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Marker("32026R0449", "replaced", null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20260322"),
            version("02015R1998-20261001"),
            c,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2026/449");
    var applies = sidenote.dates().getFirst();
    assertThat(applies.when())
        .isEqualTo(
            "2026/449 applies in stages: from 22 March 2026, its entry into force; in part from"
                + " 1 October 2026");
    assertThat(applies.clause())
        .isEqualTo(
            new Link(
                "Article 2 of 2026/449", "https://eur-lex.europa.eu/eli/reg_impl/2026/449/oj/eng"));
    assertThat(applies.after()).isEqualTo(" says which part from when");
    assertThat(sidenote.before()).isEqualTo("Before: original act");
  }

  @Test
  void datesAChangeFromTheOneOfItsActsDatesCellarHasForTheUnit() {
    var c =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Marker("32026R0449", "replaced", null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20260322"),
            version("02015R1998-20261001"),
            c,
            amendments("32026R0449", "AN PO 8.1.2.3 PTA (c)"),
            Language.ENG);

    var applies = sidenote.dates().getFirst();
    assertThat(applies.when()).isEqualTo("2026/449 applies from 1 October 2026");
    assertThat(applies.clause()).extracting(Link::label).isEqualTo("Article 2 of 2026/449");
    assertThat(applies.after()).isEmpty();
  }

  @Test
  void takesNoDayFromCellarThatTheEarlierVersionWithoutTheChangeIsAlreadyFrom() {
    var point =
        changed("anx/pnt_11.3.2", List.of(new Marker("32019R0103", "replaced", null)), List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            point,
            // Cellar has 1 February 2019 for this amendment. The version of 1 July 2021 is
            // already after that date.
            amendments("32019R0103", "AN PO 11.3.2"),
            Language.ENG);

    assertThat(sidenote.dates().getFirst().when()).startsWith("2019/103 applies in stages");
  }

  @Test
  void chainsTheActsThatChangedAUnitInTheOrderTheVersionsIntegrateThem() {
    var inserted = inserted("anx/pnt_12.14.1", new Marker("32023R0566", "replaced", null));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            inserted,
            // The amendments are at point 12.14. legal-text keeps the points numbered under
            // 12.14 beside it.
            amendmentsAt("AN PO 12.14", "AN CHA 12.14"),
            Language.ENG);

    // The marker names only the last act that changed the unit.
    assertThat(sidenote.made()).isEqualTo("Inserted by 2019/103, then replaced by 2023/566");
    assertThat(sidenote.dates()).hasSize(2);
  }

  @Test
  void saysAUnitStillThereWasAmendedWhereWordsWereDeletedFromIt() {
    var indent =
        inserted(
            "anx/level_ATTACHMENT-3-B/level_1/idt_9",
            new Marker("32019R0413", "inserted", "anx/level_ATTACHMENT-3-B"));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            indent,
            // 2022/421 and 2025/920 changed words of appendix 3-B. The indent is in that
            // appendix.
            amendmentsAt("AN CHA 3 APP 3-B TXT"),
            Language.ENG);

    assertThat(sidenote.made())
        .isEqualTo("Inserted by 2019/413, then amended by 2022/421, then amended by 2025/920");
  }

  @Test
  void namesTheActCellarSaysDeletedAUnitWhichLeavesNoMarker() {
    var deleted =
        new Difference(
            "anx/pnt_6.8.1.8",
            Kind.DELETED,
            point("anx/pnt_6.8.1.8"),
            unit("anx/pnt_6.8.1.8"),
            0,
            0,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20260101"),
            version("02015R1998-20260322"),
            deleted,
            amendments("32026R0449", "AN PO 6.8.1.8"),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Deleted by 2026/449");
    assertThat(sidenote.dates())
        .extracting(SidenoteView.Applies::when)
        .containsExactly("2026/449 applies from 22 March 2026, its entry into force");
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
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2017/815");
    assertThat(sidenote.dates())
        .singleElement()
        .satisfies(
            applies -> {
              assertThat(applies.when()).isEqualTo("2017/815 applies from 1 June 2017");
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
            point("anx/pnt_1.0.6"),
            unit("anx/pnt_1.0.6"),
            0,
            0,
            null,
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
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Inserted by 2019/1583");
    var applies = sidenote.dates().getFirst();
    assertThat(applies.when())
        .isEqualTo("2019/1583 applies from 31 December 2021, its entry into force");
    assertThat(applies.amendedBy())
        .containsExactly(
            new Link("2020/910", "https://eur-lex.europa.eu/eli/reg_impl/2020/910/oj/eng"));
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
            List.of(),
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
            List.of(),
            Language.ENG);

    var applies = sidenote.dates().getFirst();
    // Its date of application in Cellar is a placeholder. It enters into force on 16 March
    // 2019.
    assertThat(
            applies.when() + applies.between() + Objects.requireNonNull(applies.clause()).label())
        .isEqualTo("2019/413 applies from a date set in Article 3 of 2019/413");
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
            AVSEC,
            version("02015R1998-20260322"),
            version("02015R1998-20261001"),
            f,
            List.of(),
            Language.ENG);

    // The line is never empty. It says that no act is named.
    assertThat(sidenote.made()).isEqualTo("Amended by an act not named");
    assertThat(sidenote.why())
        .isEqualTo(
            "Neither the text’s markers nor the Publications Office’s data name an act between"
                + " these versions for it.");
    assertThat(sidenote.dates()).isEmpty();
    assertThat(sidenote.before()).isEqualTo("Before: as replaced by 2023/566");
  }

  @Test
  void placesActsThatReplacedAUnitTogetherInTheFirstVersionThatHasThemAll() {
    // 2019/103 applies in stages, in the versions of 1 February 2019 and 31 December 2021. It
    // replaced 11.1.2 together with 2019/1583. Only the second version has 2019/1583.
    var point =
        changed(
            "anx/pnt_11.1.2",
            List.of(
                new Marker("32019R1583", "replaced", null),
                new Marker("32019R0103", "replaced", null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            point,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2019/103 and 2019/1583 together");
  }

  @Test
  void placesTheActTheMarkerNamesLastAfterThoseOnlyCellarNames() {
    // The text keeps 4.1.3.2 struck through under the deletion by 2019/103. Cellar has 2017/815
    // replacing it, and also 2019/413, as the data of the dev store has. 2019/103 applies in
    // stages, and its first stage is before 2019/413.
    var struck = deleted("anx/pnt_4.1.3.2", new Marker("32019R0103", "deleted", null));
    var cellar = new ArrayList<>(amendments("32017R0815", "AN PO 4.1.3.2"));
    var replaced = cellar.getFirst();
    cellar.add(
        new Amendment(
            new ActIdentifier("32019R0413"),
            replaced.location(),
            replaced.role(),
            null,
            null,
            replaced.languages()));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            struck,
            cellar,
            Language.ENG);

    assertThat(sidenote.made())
        .isEqualTo("Replaced by 2017/815, then replaced by 2019/413, then deleted by 2019/103");
  }

  @Test
  void namesTheActThatReplacedTheUnitADeletedOneWasInWhole() {
    var a =
        deleted(
            "anx/level_PART-10/pnt_a", new Marker("32017R0815", "replaced", "anx/level_PART-10"));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            a,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Deleted by 2017/815");
    assertThat(sidenote.why())
        .isEqualTo("2017/815 replaced Part 10 as a whole; this point is not in it.");
  }

  @Test
  void saysActsOfOneVersionTheMarkersNameMadeItTogetherFollowingTheMarkersOverCellar() {
    var point =
        changed(
            "anx/pnt_11.1.2",
            List.of(
                new Marker("32019R1583", "replaced", null),
                new Marker("32019R0103", "replaced", null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            point,
            // Cellar also names 2020/910, which is in the same version. The markers do not name
            // it.
            amendmentsAt("AN PO 11.1.2"),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2019/103 and 2019/1583 together");
    assertThat(sidenote.acts())
        .containsExactly(new ActIdentifier("32019R0103"), new ActIdentifier("32019R1583"));
    assertThat(sidenote.why())
        .isEqualTo("There is one wording, so the change can’t be split between them.");
  }

  @Test
  void saysAChangeActsMadeTogetherIsTheirCombinedOneLimitedToOneOfThem() {
    var point =
        changed(
            "anx/pnt_11.1.2",
            List.of(
                new Marker("32019R1583", "replaced", null),
                new Marker("32019R0103", "replaced", null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            point,
            List.of(),
            new ActIdentifier("32019R1583"),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2019/1583, together with 2019/103");
    assertThat(sidenote.why())
        .isEqualTo(
            "Together with 2019/103. Both acts changed this point in the same version, and the"
                + " wording between their changes exists in no version, so this is their combined"
                + " change, not 2019/1583’s part alone.");
  }

  @Test
  void saysOnlyCellarNamesTheActWhereTheTextHasNoMarker() {
    var deleted =
        new Difference(
            "anx/pnt_1.2.3.8",
            Kind.DELETED,
            point("anx/pnt_1.2.3.8"),
            unit("anx/pnt_1.2.3.8"),
            0,
            0,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            deleted,
            amendmentsAt("AN PO 1.2.3"),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Deleted by 2019/103");
    assertThat(sidenote.why())
        .isEqualTo(
            "The text has no marker here; the Publications Office’s data names 2019/103 for"
                + " point 1.2.3.");
  }

  @Test
  void saysAPointIsNewInTheUnitAnActReplacedWhole() {
    var d =
        inserted("anx/pnt_11.1.3/pnt_d", new Marker("32019R0103", "replaced", "anx/pnt_11.1.3"));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            d,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Inserted by 2019/103");
    assertThat(sidenote.why())
        .isEqualTo("2019/103 replaced point 11.1.3 as a whole; this point is new in it.");
  }

  @Test
  void namesTheActsOfTheOneVersionOneOfWhichMadeItWhereNothingSaysWhich() {
    var point = changed("anx/pnt_11.3", List.of(), List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            point,
            List.of(),
            new ActIdentifier("32019R1583"),
            Language.ENG);

    assertThat(sidenote.made())
        .isEqualTo("Amended by an act not known: 2019/103, 2019/1583 or 2020/910");
    assertThat(sidenote.acts()).isEmpty();
    assertThat(sidenote.candidates()).hasSize(3);
    assertThat(sidenote.why()).startsWith("May be 2019/1583’s change");
  }

  /** Cellar's amendments of 2015/1998 by {@code act} at {@code location}. */
  private static List<Amendment> amendments(String act, String location) {
    return AVSEC.amendments().stream()
        .filter(amendment -> amendment.act().celex().equals(act))
        .filter(amendment -> amendment.location().code().equals(location))
        .toList();
  }

  /** Cellar's amendments of 2015/1998 at {@code locations}, by any act. */
  private static List<Amendment> amendmentsAt(String... locations) {
    var codes = List.of(locations);
    return AVSEC.amendments().stream()
        .filter(amendment -> codes.contains(amendment.location().code()))
        .toList();
  }

  private static Difference inserted(String eId, Marker marker) {
    return new Difference(
        eId,
        Kind.INSERTED,
        point(eId),
        unit(eId),
        0,
        0,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(marker),
        List.of());
  }

  private static Difference deleted(String eId, Marker marker) {
    return new Difference(
        eId,
        Kind.DELETED,
        point(eId),
        unit(eId),
        0,
        0,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(marker),
        List.of());
  }

  private static Difference changed(String eId, List<Marker> markers, List<Marker> earlier) {
    return new Difference(
        eId,
        Kind.CHANGED,
        point(eId),
        unit(eId),
        0,
        0,
        null,
        null,
        null,
        null,
        List.of(),
        markers,
        earlier);
  }

  private static Sibling point(String eId) {
    return new Sibling(eId, "point", "");
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
