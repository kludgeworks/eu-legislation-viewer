// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
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
 * sidenote uses two sources. The first is the tags that the converter keeps on the occurrences of
 * the consolidations of 2015/1998. The second is Cellar's amendments of those occurrences. The
 * versions, dates and amendments are Cellar's, recorded.
 */
class SidenoteViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));

  @Test
  void namesTheActBetweenTheVersionsAndThePartsItAppliesInWhereCellarDoesntSayWhich() {
    var c =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Tag("32026R0449", Modification.REPLACED, null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20260322"),
            version("02015R1998-20261001"),
            c,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2026/449");
    var applies = sidenote.dates().getFirst();
    assertThat(applies.when())
        .isEqualTo(
            "2026/449 · OJ L, 2026/449, 2.3.2026 · applies in parts: from 22 March 2026, its entry"
                + " into force; in part from 1 October 2026");
    assertThat(applies.clause())
        .isEqualTo(
            new Link(
                "Article 2 of 2026/449", "https://eur-lex.europa.eu/eli/reg_impl/2026/449/oj/eng"));
    assertThat(applies.after()).isEqualTo(" says which part from when");
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

    var listed =
        SidenoteView.of(
            AVSEC, Inclusions.listed(AVSEC.timeline()), from, to, c, List.of(), Language.ENG);
    var included =
        SidenoteView.of(
            AVSEC,
            new Inclusions(AVSEC.timeline(), Map.of(to, List.of(tagged))),
            from,
            to,
            c,
            List.of(),
            Language.ENG);

    assertThat(listed.attribution().acts()).doesNotContain(tagged);
    assertThat(included.made()).isEqualTo("Replaced by %s", AVSEC.cited(tagged));
    assertThat(included.attribution().acts()).containsExactly(tagged);
  }

  @Test
  void namesAnActThatAnEarlierVersionIncludedByItsNewTagWhereAPartOfItAppliesInTheStep() {
    // As 2024/1619 on 2013/36 on 11 January 2026, for which Cellar lists no act. Here 2026/449 on
    // 2015/1998: included from 22 March 2026, and in part from 1 October 2026. Cellar's listing of
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
    var tag = new Tag(amending.celex(), Modification.REPLACED, null);

    var newTag = changed("anx/pnt_8.1.2.3/pnt_c", List.of(tag), List.of());
    var noPart =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Tag("32019R0103", Modification.REPLACED, null)),
            List.of());

    assertThat(of(included, from, to, newTag).made()).isEqualTo("Replaced by 2026/449");
    assertThat(of(included, from, to, newTag).dates().getFirst().when())
        .contains("from 1 October 2026");
    // No part of 2019/103 applies in the step: its tag is old, not new.
    assertThat(of(included, from, to, noPart).attribution().acts()).isEmpty();
  }

  private static SidenoteView of(
      Inclusions included, Version from, Version to, Difference difference) {
    return SidenoteView.of(AVSEC, included, from, to, difference, List.of(), Language.ENG);
  }

  @Test
  void datesACorrectionFromTheStartOfTheActItCorrectsAndTheVersionThatFirstHasIt() {
    var c =
        changed(
            "anx/pnt_11.3.2",
            List.of(new Tag("32019R0103R(01)", Modification.CORRECTED, null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            c,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Corrected by Corrigendum 1 to 2019/103");
    assertThat(sidenote.dates())
        .extracting(SidenoteView.Applies::when)
        .containsExactly(
            "Corrigendum 1 to 2019/103 corrects 2019/103 from 31 December 2021 · first in the text"
                + " of 31 December 2021");
  }

  @Test
  void datesAChangeFromTheOneOfItsActsDatesCellarHasForTheOccurrence() {
    var c =
        changed(
            "anx/pnt_8.1.2.3/pnt_c",
            List.of(new Tag("32026R0449", Modification.REPLACED, null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20260322"),
            version("02015R1998-20261001"),
            c,
            amendments("32026R0449", "AN PO 8.1.2.3 PTA (c)"),
            Language.ENG);

    var applies = sidenote.dates().getFirst();
    assertThat(applies.when())
        .isEqualTo("2026/449 · OJ L, 2026/449, 2.3.2026 · applies from 1 October 2026");
    assertThat(applies.clause()).extracting(Link::label).isEqualTo("Article 2 of 2026/449");
    assertThat(applies.after()).isEmpty();
  }

  @Test
  void takesNoDayFromCellarThatTheEarlierVersionWithoutTheChangeIsAlreadyFrom() {
    var point =
        changed(
            "anx/pnt_11.3.2",
            List.of(new Tag("32019R0103", Modification.REPLACED, null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            point,
            // Cellar has 1 February 2019 for this amendment. The version of 1 July 2021 is
            // already after that date.
            amendments("32019R0103", "AN PO 11.3.2"),
            Language.ENG);

    assertThat(sidenote.dates().getFirst().when())
        .startsWith("2019/103 · OJ L 21, 24.1.2019 · applies in parts");
  }

  @Test
  void chainsTheActsThatChangedAnOccurrenceInTheOrderTheVersionsIntegrateThem() {
    var inserted = inserted("anx/pnt_12.14.1", new Tag("32023R0566", Modification.REPLACED, null));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            inserted,
            // The amendments are at point 12.14. legal-text keeps the points numbered under
            // 12.14 beside it.
            amendmentsAt("AN PO 12.14", "AN CHA 12.14"),
            Language.ENG);

    // The tag names only the last act that changed the occurrence.
    assertThat(sidenote.made()).isEqualTo("Inserted by 2019/103, then replaced by 2023/566");
    assertThat(sidenote.dates()).hasSize(2);
  }

  @Test
  void saysAnOccurrenceStillThereWasAmendedWhereWordsWereDeletedFromIt() {
    var indent =
        inserted(
            "anx/level_ATTACHMENT-3-B/level_1/idt_9",
            new Tag("32019R0413", Modification.INSERTED, "anx/level_ATTACHMENT-3-B"));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
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
  void namesTheActCellarSaysDeletedAnOccurrenceWhichLeavesNoTag() {
    var deleted =
        new Difference(
            "anx/pnt_6.8.1.8",
            Kind.DELETED,
            point("anx/pnt_6.8.1.8"),
            occurrence("anx/pnt_6.8.1.8"),
            0,
            0,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            null);

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20260101"),
            version("02015R1998-20260322"),
            deleted,
            amendments("32026R0449", "AN PO 6.8.1.8"),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Deleted by 2026/449");
    assertThat(sidenote.dates())
        .extracting(SidenoteView.Applies::when)
        .containsExactly(
            "2026/449 · OJ L, 2026/449, 2.3.2026 · applies from 22 March 2026, its entry into"
                + " force");
  }

  @Test
  void datesAChangeFromTheActsOneDateOfApplication() {
    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20160229"),
            version("02015R1998-20170607"),
            changed(
                "anx/pnt_1.0.3",
                List.of(new Tag("32017R0815", Modification.REPLACED, null)),
                List.of()),
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2017/815");
    assertThat(sidenote.dates())
        .singleElement()
        .satisfies(
            applies -> {
              assertThat(applies.when())
                  .isEqualTo("2017/815 · OJ L 122, 13.5.2017 · applies from 1 June 2017");
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
            occurrence("anx/pnt_1.0.6"),
            0,
            0,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(new Tag("32019R1583", Modification.INSERTED, null)),
            List.of(),
            null);

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            inserted,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Inserted by 2019/1583");
    var applies = sidenote.dates().getFirst();
    assertThat(applies.when())
        .isEqualTo(
            "2019/1583 · OJ L 246, 26.9.2019 · applies from 31 December 2021, its entry into"
                + " force");
    assertThat(applies.amendedBy())
        .containsExactly(
            new Link("2020/910", "https://eur-lex.europa.eu/eli/reg_impl/2020/910/oj/eng"));
  }

  @Test
  void namesTheActsThatChangedAPointInOneVersionTogether() {
    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            changed(
                "anx/pnt_11.1.2",
                List.of(
                    new Tag("32019R0103", Modification.REPLACED, "anx/pnt_11.1"),
                    new Tag("32019R1583", Modification.REPLACED, null)),
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
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20200702"),
            version("02015R1998-20210315"),
            changed(
                "anx/pnt_1.1.3.4",
                List.of(new Tag("32019R0413", Modification.REPLACED, null)),
                List.of()),
            List.of(),
            Language.ENG);

    var applies = sidenote.dates().getFirst();
    // Its date of application in Cellar is a placeholder. It enters into force on 16 March
    // 2019.
    assertThat(
            applies.when() + applies.between() + Objects.requireNonNull(applies.clause()).label())
        .isEqualTo(
            "2019/413 · OJ L 73, 15.3.2019 · applies from a date set in Article 3 of 2019/413");
  }

  @Test
  void claimsNoActOutsideTheVersionsAndSaysWhatTheOccurrenceWasBefore() {
    var f =
        changed(
            "anx/pnt_8.1.2.3/pnt_f",
            List.of(new Tag("32023R0566", Modification.REPLACED, null)),
            List.of(new Tag("32023R0566", Modification.REPLACED, null)));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20260322"),
            version("02015R1998-20261001"),
            f,
            List.of(),
            Language.ENG);

    // The line is never empty. It says that no act is named.
    assertThat(sidenote.made()).isEqualTo("The data does not say which act amended this.");
    assertThat(sidenote.dates()).isEmpty();
  }

  @Test
  void namesTheVersionWhereAChangeNothingAttributesFirstAppearsAndWhatItConsolidates() {
    // As points 12.9.3.14 and 12.9.3.15 of 2015/1998, deleted in the text of 22 March 2026.
    var deleted = deleted("anx/pnt_12.9.3.14", new Tag("32019R0103", Modification.REPLACED, null));
    var march = version("02015R1998-20260322");
    var included = Inclusions.listed(AVSEC.timeline());

    var sidenote = SidenoteView.appeared(AVSEC, included, march, deleted);

    assertThat(sidenote.made())
        .startsWith("Deleted in the text of 22 March 2026, which consolidates ")
        .contains("2026/449");
    // It names no act as the one that made it.
    assertThat(sidenote.attribution().acts()).isEmpty();
    assertThat(sidenote.attribution().candidates()).isEmpty();
    assertThat(sidenote.dates()).isEmpty();
  }

  @Test
  void namesOnlyTheVersionWhereCellarListsNoActForIt() {
    var march = version("02015R1998-20260322");
    var empty =
        new Version(
            march.kind(),
            march.date(),
            march.celex(),
            march.interim(),
            List.of(),
            march.languages(),
            march.formex());
    var changed = changed("anx/pnt_12.9.3.14", List.of(), List.of());

    var sidenote =
        SidenoteView.appeared(AVSEC, Inclusions.listed(AVSEC.timeline()), empty, changed);

    assertThat(sidenote.made()).isEqualTo("Amended in the text of 22 March 2026");
  }

  @Test
  void placesActsThatReplacedAnOccurrenceTogetherInTheFirstVersionThatHasThemAll() {
    // 2019/103 applies in parts, in the versions of 1 February 2019 and 31 December 2021. It
    // replaced 11.1.2 together with 2019/1583. Only the second version has 2019/1583.
    var point =
        changed(
            "anx/pnt_11.1.2",
            List.of(
                new Tag("32019R1583", Modification.REPLACED, null),
                new Tag("32019R0103", Modification.REPLACED, null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            point,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2019/103 and 2019/1583 together");
  }

  @Test
  void placesTheActTheTagNamesLastAfterThoseOnlyCellarNames() {
    // The text keeps 4.1.3.2 as a kept deletion, under the deletion by 2019/103. Cellar has
    // 2017/815
    // replacing it, and also 2019/413, as the data of the dev store has. 2019/103 applies in
    // parts, and its first part is before 2019/413.
    var kept = deleted("anx/pnt_4.1.3.2", new Tag("32019R0103", Modification.DELETED, null));
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
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            kept,
            cellar,
            Language.ENG);

    assertThat(sidenote.made())
        .isEqualTo("Replaced by 2017/815, then replaced by 2019/413, then deleted by 2019/103");
  }

  @Test
  void namesTheActThatReplacedTheOccurrenceADeletedOneWasInWhole() {
    var a =
        deleted(
            "anx/level_PART-10/pnt_a",
            new Tag("32017R0815", Modification.REPLACED, "anx/level_PART-10"));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            a,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Deleted by 2017/815");
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

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            point,
            // Cellar also names 2020/910, which is in the same version. The tags do not name
            // it.
            amendmentsAt("AN PO 11.1.2"),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2019/103 and 2019/1583 together");
    assertThat(sidenote.attribution().acts())
        .containsExactly(new ActIdentifier("32019R0103"), new ActIdentifier("32019R1583"));
  }

  @Test
  void namesTheOtherActsOfAChangeMadeTogetherLimitedToOneOfThem() {
    var point =
        changed(
            "anx/pnt_11.1.2",
            List.of(
                new Tag("32019R1583", Modification.REPLACED, null),
                new Tag("32019R0103", Modification.REPLACED, null)),
            List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            point,
            List.of(),
            new ActIdentifier("32019R1583"),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2019/1583, together with 2019/103");
  }

  @Test
  void namesTheActCellarNamesWhereTheTextHasNoTag() {
    var deleted =
        new Difference(
            "anx/pnt_1.2.3.8",
            Kind.DELETED,
            point("anx/pnt_1.2.3.8"),
            occurrence("anx/pnt_1.2.3.8"),
            0,
            0,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            null);

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            deleted,
            amendmentsAt("AN PO 1.2.3"),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Deleted by 2019/103");
  }

  @Test
  void namesEachActCellarNamesForOneOccurrence() {
    var deleted =
        new Difference(
            "anx/pnt_12.3.1",
            Kind.DELETED,
            point("anx/pnt_12.3.1"),
            occurrence("anx/pnt_12.3.1"),
            0,
            0,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            null);

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20160229"),
            version("02015R1998-20261001"),
            deleted,
            amendmentsAt("AN PO 12.3.1"),
            Language.ENG);

    assertThat(sidenote.attribution().acts())
        .extracting(ActIdentifier::cited)
        .containsExactlyInAnyOrder("2021/255", "2022/421");
  }

  @Test
  void namesTheActThatReplacedWholeTheOccurrenceAnInsertedPointIsIn() {
    var d =
        inserted(
            "anx/pnt_11.1.3/pnt_d", new Tag("32019R0103", Modification.REPLACED, "anx/pnt_11.1.3"));

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            d,
            List.of(),
            Language.ENG);

    assertThat(sidenote.made()).isEqualTo("Inserted by 2019/103");
  }

  @Test
  void namesTheActsOfTheOneVersionOneOfWhichMadeItWhereNothingSaysWhich() {
    var point = changed("anx/pnt_11.3", List.of(), List.of());

    var sidenote =
        SidenoteView.of(
            AVSEC,
            Inclusions.listed(AVSEC.timeline()),
            version("02015R1998-20210701"),
            version("02015R1998-20211231"),
            point,
            List.of(),
            new ActIdentifier("32019R1583"),
            Language.ENG);

    assertThat(sidenote.made())
        .isEqualTo(
            "The data does not say which act amended this. It was 2019/103, 2019/1583 or"
                + " 2020/910.");
    assertThat(sidenote.attribution().acts()).isEmpty();
    assertThat(sidenote.attribution().candidates()).hasSize(3);
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
    return new Difference(
        eId,
        Kind.INSERTED,
        point(eId),
        occurrence(eId),
        0,
        0,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(tag),
        List.of(),
        null);
  }

  private static Difference deleted(String eId, Tag tag) {
    return new Difference(
        eId,
        Kind.DELETED,
        point(eId),
        occurrence(eId),
        0,
        0,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(tag),
        List.of(),
        null);
  }

  private static Difference changed(String eId, List<Tag> tags, List<Tag> earlier) {
    return new Difference(
        eId,
        Kind.CHANGED,
        point(eId),
        occurrence(eId),
        0,
        0,
        null,
        null,
        null,
        null,
        List.of(),
        tags,
        earlier,
        null);
  }

  private static Sibling point(String eId) {
    return new Sibling(eId, "point", "");
  }

  private static Element occurrence(String eId) {
    return new Element("occurrence", Map.of("eId", eId), List.of(), "");
  }

  private static Version version(String celex) {
    return AVSEC.timeline().versions().stream()
        .filter(v -> v.celex().equals(celex))
        .findFirst()
        .orElseThrow();
  }
}
