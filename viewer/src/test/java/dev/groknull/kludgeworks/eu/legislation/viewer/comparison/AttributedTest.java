// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tests who made a difference in 2015/1998, and from when each change applies ({@link
 * Attributed#of}). There are two sources. The first is the tags that the converter keeps on the
 * occurrences of the consolidations of 2015/1998. The second is Cellar's amendments of those
 * occurrences. The versions, dates and amendments are Cellar's, recorded.
 */
class AttributedTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final Inclusions LISTED = Inclusions.listed(AVSEC.timeline());

  @Test
  void namesTheActBetweenTheVersionsAndThePartsItAppliesInWhereCellarDoesntSayWhich() {
    var c =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Tag("32026R0449", Modification.REPLACED, null)),
            List.of());

    var attributed = of("02015R1998-20260322", "02015R1998-20261001", c, List.of());

    assertThat(steps(attributed)).containsExactly("replaced 2026/449");
    assertThat(applying(attributed.applies().getFirst()))
        .isInstanceOf(Applying.FromMoreThanOneDay.class);
  }

  @Test
  void namesAnActThatTheTextTagsFirstThoughCellarDoesNotListIt() {
    // As 767/2008 of 29 December 2017, which tags 2017/2226 before Cellar lists it.
    var tagged = new ActIdentifier("32017R2226");
    var c =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Tag(tagged.celex(), Modification.REPLACED, null)),
            List.of());
    var from = version("02015R1998-20260322");
    var to = version("02015R1998-20261001");

    var listed = Attributed.of(AVSEC, LISTED, from, to, c, List.of(), List.of());
    var included =
        Attributed.of(
            AVSEC,
            new Inclusions(AVSEC.timeline(), Map.of(to, List.of(tagged))),
            from,
            to,
            c,
            List.of(),
            List.of());

    assertThat(listed.attribution().acts()).doesNotContain(tagged);
    assertThat(included.attribution().acts()).containsExactly(tagged);
  }

  @Test
  void namesAnActThatAnEarlierVersionIncludedByItsNewTagWhereAPartOfItAppliesInTheStep() {
    // As 2024/1619 on 2013/36 on 11 January 2026, for which Cellar lists no act. Here 2026/449 on
    // 2015/1998: included from 22 March 2026, and a part from 1 October 2026. Cellar's listing of
    // it in October is taken away.
    var amending = new ActIdentifier("32026R0449");
    var from = version("02015R1998-20260322");
    var listed = version("02015R1998-20261001");
    var to =
        new Version(
            listed.kind(),
            listed.date(),
            listed.celex(),
            listed.interim(),
            List.of(),
            listed.languages(),
            listed.formex());
    var versions = new ArrayList<>(AVSEC.timeline().versions());
    versions.set(versions.indexOf(listed), to);
    var included = Inclusions.listed(new Timeline(versions));
    var newTag =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Tag(amending.celex(), Modification.REPLACED, null)),
            List.of());
    var noPart =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Tag("32019R0103", Modification.REPLACED, null)),
            List.of());

    var named = Attributed.of(AVSEC, included, from, to, newTag, List.of(), List.of());

    assertThat(steps(named)).containsExactly("replaced 2026/449");
    assertThat(named.applies().getFirst().toString()).contains("2026-10-01");
    // No part of 2019/103 applies in the step: its tag is old, not new.
    assertThat(
            Attributed.of(AVSEC, included, from, to, noPart, List.of(), List.of())
                .attribution()
                .acts())
        .isEmpty();
  }

  @Test
  void datesACorrectionFromTheStartOfTheActItCorrectsAndTheVersionThatFirstHasIt() {
    var c =
        changed(
            "anx/pnt_11.3.2",
            List.of(new Tag("32019R0103R(01)", Modification.CORRECTED, null)),
            List.of());

    var attributed = of("02015R1998-20210701", "02015R1998-20211231", c, List.of());

    assertThat(steps(attributed)).containsExactly("corrected Corrigendum 1 to 2019/103");
    assertThat(attributed.applies())
        .containsExactly(
            new Applicability.Corrects(
                new ActIdentifier("32019R0103R(01)"),
                new ActIdentifier("32019R0103"),
                LocalDate.of(2021, 12, 31),
                LocalDate.of(2021, 12, 31)));
  }

  @Test
  void datesAChangeFromTheOneOfItsActsDatesCellarHasForTheOccurrence() {
    var c =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Tag("32026R0449", Modification.REPLACED, null)),
            List.of());

    var attributed =
        of(
            "02015R1998-20260322",
            "02015R1998-20261001",
            c,
            amendments("32026R0449", "AN PO 8.1.2.3 PTA (c)"));

    assertThat(applying(attributed.applies().getFirst()))
        .isInstanceOfSatisfying(
            Applying.From.class,
            from -> assertThat(from.day()).isEqualTo(LocalDate.of(2026, 10, 1)));
  }

  @Test
  void takesNoDayFromCellarThatTheEarlierVersionWithoutTheChangeIsAlreadyFrom() {
    var point =
        changed(
            "anx/pnt_11.3.2",
            List.of(new Tag("32019R0103", Modification.REPLACED, null)),
            List.of());

    var attributed =
        of(
            "02015R1998-20210701",
            "02015R1998-20211231",
            point,
            // Cellar has 1 February 2019 for this amendment. The version of 1 July 2021 is
            // already after that date.
            amendments("32019R0103", "AN PO 11.3.2"));

    assertThat(applying(attributed.applies().getFirst()))
        .isInstanceOf(Applying.FromMoreThanOneDay.class);
  }

  @Test
  void chainsTheActsThatChangedAnOccurrenceInTheOrderTheVersionsIntegrateThem() {
    var inserted = inserted("anx/pnt_12.14.1", new Tag("32023R0566", Modification.REPLACED, null));

    var attributed =
        of(
            "02015R1998-20160229",
            "02015R1998-20261001",
            inserted,
            // The amendments are at point 12.14. legal-text keeps the points numbered under
            // 12.14 beside it.
            amendmentsAt("AN PO 12.14", "AN CHA 12.14"));

    // The tag names only the last act that changed the occurrence.
    assertThat(steps(attributed)).containsExactly("inserted 2019/103", "replaced 2023/566");
    assertThat(attributed.applies()).hasSize(2);
  }

  @Test
  void saysAnOccurrenceStillThereWasAmendedWhereWordsWereDeletedFromIt() {
    var indent =
        inserted(
            "anx/level_ATTACHMENT-3-B/level_1/idt_9",
            new Tag("32019R0413", Modification.INSERTED, "anx/level_ATTACHMENT-3-B"));

    var attributed =
        of(
            "02015R1998-20160229",
            "02015R1998-20261001",
            indent,
            // 2022/421 and 2025/920 changed words of appendix 3-B. The indent is in that
            // appendix.
            amendmentsAt("AN CHA 3 APP 3-B TXT"));

    assertThat(steps(attributed))
        .containsExactly("inserted 2019/413", "amended 2022/421", "amended 2025/920");
  }

  @Test
  void namesTheActCellarSaysDeletedAnOccurrenceWhichLeavesNoTag() {
    var deleted = untagged("anx/pnt_6.8.1.8", Kind.DELETED);

    var attributed =
        of(
            "02015R1998-20260101",
            "02015R1998-20260322",
            deleted,
            amendments("32026R0449", "AN PO 6.8.1.8"));

    assertThat(steps(attributed)).containsExactly("deleted 2026/449");
    assertThat(applying(attributed.applies().getFirst()))
        .isInstanceOfSatisfying(
            Applying.From.class,
            from -> {
              assertThat(from.day()).isEqualTo(LocalDate.of(2026, 3, 22));
              assertThat(from.entryIntoForce()).isTrue();
            });
  }

  @Test
  void datesAChangeFromTheActsOneDateOfApplication() {
    var attributed =
        of(
            "02015R1998-20160229",
            "02015R1998-20170607",
            changed(
                "anx/pnt_1.0.3",
                List.of(new Tag("32017R0815", Modification.REPLACED, null)),
                List.of()),
            List.of());

    assertThat(steps(attributed)).containsExactly("replaced 2017/815");
    assertThat(attributed.applies())
        .singleElement()
        .isInstanceOfSatisfying(
            Applicability.Applies.class,
            applies -> {
              assertThat(applies.applying())
                  .isInstanceOfSatisfying(
                      Applying.From.class,
                      from -> assertThat(from.day()).isEqualTo(LocalDate.of(2017, 6, 1)));
              assertThat(applies.clause()).isNotEmpty();
            });
  }

  @Test
  void datesAnActThatOnlyEntersIntoForceFromThatWithTheActThatMovedItsDate() {
    var inserted = inserted("anx/pnt_1.0.6", new Tag("32019R1583", Modification.INSERTED, null));

    var attributed = of("02015R1998-20210701", "02015R1998-20211231", inserted, List.of());

    assertThat(steps(attributed)).containsExactly("inserted 2019/1583");
    assertThat(attributed.applies().getFirst())
        .isInstanceOfSatisfying(
            Applicability.Applies.class,
            applies -> {
              assertThat(applies.applying())
                  .isInstanceOfSatisfying(
                      Applying.From.class,
                      from -> {
                        assertThat(from.day()).isEqualTo(LocalDate.of(2021, 12, 31));
                        assertThat(from.entryIntoForce()).isTrue();
                      });
              assertThat(applies.amendedBy()).containsExactly(new ActIdentifier("32020R0910"));
            });
  }

  @Test
  void namesTheActsThatChangedAPointInOneVersionTogether() {
    var attributed =
        of(
            "02015R1998-20210701",
            "02015R1998-20211231",
            changed(
                "anx/pnt_11.1.2",
                List.of(
                    new Tag("32019R0103", Modification.REPLACED, "anx/pnt_11.1"),
                    new Tag("32019R1583", Modification.REPLACED, null)),
                List.of()),
            List.of());

    assertThat(steps(attributed)).containsExactly("replaced 2019/103 and 2019/1583");
    assertThat(attributed.applies()).hasSize(2);
  }

  @Test
  void pointsToTheClauseWhereCellarHasNoDayForTheActsApplication() {
    var attributed =
        of(
            "02015R1998-20200702",
            "02015R1998-20210315",
            changed(
                "anx/pnt_1.1.3.4",
                List.of(new Tag("32019R0413", Modification.REPLACED, null)),
                List.of()),
            List.of());

    // Its date of application in Cellar is a placeholder. It enters into force on 16 March 2019.
    assertThat(attributed.applies().getFirst())
        .isInstanceOfSatisfying(
            Applicability.Applies.class,
            applies -> {
              assertThat(applies.applying()).isInstanceOf(Applying.SetBy.class);
              assertThat(applies.clause()).isNotEmpty();
            });
  }

  @Test
  void claimsNoActOutsideTheVersions() {
    var f =
        changed(
            "anx/pnt_8.1.2.3/pnt_f",
            List.of(new Tag("32023R0566", Modification.REPLACED, null)),
            List.of(new Tag("32023R0566", Modification.REPLACED, null)));

    var attributed = of("02015R1998-20260322", "02015R1998-20261001", f, List.of());

    assertThat(attributed.attribution().unattributed()).isTrue();
    assertThat(attributed.applies()).isEmpty();
  }

  @Test
  void placesActsThatReplacedAnOccurrenceTogetherInTheFirstVersionThatHasThemAll() {
    // 2019/103 applies from more than one day, in the versions of 1 February 2019 and 31 December
    // 2021. It
    // replaced 11.1.2 together with 2019/1583. Only the second version has 2019/1583.
    var point =
        changed(
            "anx/pnt_11.1.2",
            List.of(
                new Tag("32019R1583", Modification.REPLACED, null),
                new Tag("32019R0103", Modification.REPLACED, null)),
            List.of());

    var attributed = of("02015R1998-20160229", "02015R1998-20261001", point, List.of());

    assertThat(steps(attributed)).containsExactly("replaced 2019/103 and 2019/1583");
  }

  @Test
  void placesTheActTheTagNamesLastAfterThoseOnlyCellarNames() {
    // The text keeps 4.1.3.2 as a kept deletion, under the deletion by 2019/103. Cellar has
    // 2017/815 replacing it, and also 2019/413, as the data of the dev store has. 2019/103 applies
    // from more than one day, and its first part is before 2019/413.
    var kept = deleted("anx/pnt_4.1.3.2", new Tag("32019R0103", Modification.DELETED, null));
    var cellar = new ArrayList<>(amendments("32017R0815", "AN PO 4.1.3.2"));
    var replaced = cellar.getFirst();
    cellar.add(
        new Amendment(
            new ActIdentifier("32019R0413"),
            replaced.location(),
            replaced.place(),
            replaced.role(),
            null,
            null,
            replaced.languages()));

    var attributed = of("02015R1998-20160229", "02015R1998-20261001", kept, cellar);

    assertThat(steps(attributed))
        .containsExactly("replaced 2017/815", "replaced 2019/413", "deleted 2019/103");
  }

  @Test
  void namesTheActThatReplacedTheOccurrenceADeletedOneWasInWhole() {
    var a =
        deleted(
            "anx/level_PART-10/pnt_a",
            new Tag("32017R0815", Modification.REPLACED, "anx/level_PART-10"));

    var attributed = of("02015R1998-20160229", "02015R1998-20261001", a, List.of());

    assertThat(steps(attributed)).containsExactly("deleted 2017/815");
  }

  @Test
  void saysActsOfOneVersionTheTagsNameMadeItTogetherFollowingTheTagsOverCellar() {
    var point =
        changed(
            "anx/pnt_11.1.2",
            List.of(
                new Tag("32019R1583", Modification.REPLACED, null),
                new Tag("32019R0103", Modification.REPLACED, null)),
            List.of());

    var attributed =
        of(
            "02015R1998-20210701",
            "02015R1998-20211231",
            point,
            // Cellar also names 2020/910, which is in the same version. The tags do not name it.
            amendmentsAt("AN PO 11.1.2"));

    assertThat(steps(attributed)).containsExactly("replaced 2019/103 and 2019/1583");
    assertThat(attributed.attribution().acts())
        .containsExactly(new ActIdentifier("32019R0103"), new ActIdentifier("32019R1583"));
  }

  @Test
  void namesTheActCellarNamesWhereTheTextHasNoTag() {
    var deleted = untagged("anx/pnt_1.2.3.8", Kind.DELETED);

    var attributed =
        of("02015R1998-20210701", "02015R1998-20211231", deleted, amendmentsAt("AN PO 1.2.3"));

    assertThat(steps(attributed)).containsExactly("deleted 2019/103");
  }

  @Test
  void namesEachActCellarNamesForOneOccurrence() {
    var deleted = untagged("anx/pnt_12.3.1", Kind.DELETED);

    var attributed =
        of("02015R1998-20160229", "02015R1998-20261001", deleted, amendmentsAt("AN PO 12.3.1"));

    assertThat(attributed.attribution().acts())
        .extracting(ActIdentifier::cited)
        .containsExactlyInAnyOrder("2021/255", "2022/421");
  }

  @Test
  void namesTheActThatReplacedWholeTheOccurrenceAnInsertedPointIsIn() {
    var d =
        inserted(
            "anx/pnt_11.1.3/pnt_d", new Tag("32019R0103", Modification.REPLACED, "anx/pnt_11.1.3"));

    var attributed = of("02015R1998-20210701", "02015R1998-20211231", d, List.of());

    assertThat(steps(attributed)).containsExactly("inserted 2019/103");
  }

  @Test
  void namesTheActsOfTheOneVersionOneOfWhichMadeItWhereNothingSaysWhich() {
    var point = changed("anx/pnt_11.3", List.of(), List.of());

    var attributed = of("02015R1998-20210701", "02015R1998-20211231", point, List.of());

    assertThat(attributed.attribution().acts()).isEmpty();
    assertThat(attributed.attribution().candidates())
        .extracting(ActIdentifier::cited)
        .containsExactly("2019/103", "2019/1583", "2020/910");
  }

  @Test
  void namesTheTextWhereAChangeNothingAttributesFirstAppearsAndWhatItConsolidates() {
    // As points 12.9.3.14 and 12.9.3.15 of 2015/1998, deleted in the text of 22 March 2026.
    var deleted = deleted("anx/pnt_12.9.3.14", new Tag("32019R0103", Modification.REPLACED, null));
    var march = version("02015R1998-20260322");

    var attributed = new Attributed(deleted, Attribution.NONE).appearedIn(march, LISTED);

    var appeared = Objects.requireNonNull(attributed.appeared());
    assertThat(appeared.version()).isEqualTo(march);
    assertThat(appeared.consolidates()).contains(new ActIdentifier("32026R0449"));
    // It names no act as the one that made it.
    assertThat(attributed.attribution()).isEqualTo(Attribution.NONE);
    assertThat(attributed.applies()).isEmpty();
  }

  private static Attributed of(
      String from, String to, Difference difference, List<Amendment> amendments) {
    return Attributed.of(
        AVSEC, LISTED, version(from), version(to), difference, amendments, List.of());
  }

  /** Returns the steps of {@code attributed} as words: the modification, then the acts. */
  private static List<String> steps(Attributed attributed) {
    return attributed.attribution().steps().stream()
        .map(step ->
            step.modification().word()
                + " "
                + step.acts().stream().map(AVSEC::cited).collect(Collectors.joining(" and ")))
        .toList();
  }

  private static Applying applying(Applicability applicability) {
    return ((Applicability.Applies) applicability).applying();
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

  private static Difference inserted(String eId, Tag tag) {
    return difference(eId, Kind.INSERTED, List.of(tag), List.of());
  }

  private static Difference deleted(String eId, Tag tag) {
    return difference(eId, Kind.DELETED, List.of(tag), List.of());
  }

  private static Difference untagged(String eId, Kind kind) {
    return difference(eId, kind, List.of(), List.of());
  }

  private static Difference changed(String eId, List<Tag> tags, List<Tag> earlier) {
    return difference(eId, Kind.CHANGED, tags, earlier);
  }

  private static Difference difference(String eId, Kind kind, List<Tag> tags, List<Tag> earlier) {
    return new Difference(
        eId,
        kind,
        new Sibling(eId, "point", ""),
        new Element("occurrence", Map.of("eId", eId), List.of(), ""),
        0,
        0,
        null,
        null,
        null,
        null,
        List.of(),
        tags,
        earlier,
        null,
        null);
  }

  private static Version version(String celex) {
    return AVSEC.timeline().versions().stream()
        .filter(v -> v.celex().equals(celex))
        .findFirst()
        .orElseThrow();
  }
}
