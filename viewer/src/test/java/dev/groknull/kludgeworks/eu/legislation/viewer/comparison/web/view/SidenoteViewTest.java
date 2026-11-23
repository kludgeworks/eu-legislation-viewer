// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Applicability;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributed;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView.Link;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests how the sidenote of {@link SidenoteView} says who made a difference of 2015/1998, in words.
 * Who made it is given: {@code AttributedTest} tests how the domain finds it. The act's numbers and
 * Official Journal references are Cellar's, recorded.
 */
class SidenoteViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

  static final ActIdentifier A2017_815 = new ActIdentifier("32017R0815");
  static final ActIdentifier A2019_103 = new ActIdentifier("32019R0103");
  static final ActIdentifier A2019_413 = new ActIdentifier("32019R0413");
  static final ActIdentifier A2019_1583 = new ActIdentifier("32019R1583");
  static final ActIdentifier A2020_910 = new ActIdentifier("32020R0910");
  static final ActIdentifier A2026_449 = new ActIdentifier("32026R0449");

  @Test
  void saysTheActAndThePartsItAppliesIn() {
    var applies =
        new Applicability.Applies(
            A2026_449,
            new Applying.InParts(
                List.of(
                    new Applying.Start(LocalDate.of(2026, 3, 22), true, false),
                    new Applying.Start(LocalDate.of(2026, 10, 1), false, true)),
                LocalDate.of(2026, 3, 22),
                List.of()),
            List.of(article("2")),
            List.of());

    var sidenote = sidenote(step(Modification.REPLACED, A2026_449), applies);

    assertThat(sidenote.made()).isEqualTo("Replaced by 2026/449");
    var line = sidenote.dates().getFirst();
    assertThat(line.when())
        .isEqualTo(
            "2026/449 · OJ L, 2026/449, 2.3.2026 · applies in parts: from 22 March 2026, its entry"
                + " into force; in part from 1 October 2026");
    assertThat(line.clause())
        .isEqualTo(
            new Link(
                "Article 2 of 2026/449", "https://eur-lex.europa.eu/eli/reg_impl/2026/449/oj/eng"));
    assertThat(line.after()).isEqualTo(" says which part from when");
  }

  @Test
  void datesACorrectionFromTheStartOfTheActItCorrectsAndTheTextThatFirstHasIt() {
    var corrigendum = new ActIdentifier("32019R0103R(01)");
    var corrects =
        new Applicability.Corrects(
            corrigendum, A2019_103, LocalDate.of(2021, 12, 31), LocalDate.of(2021, 12, 31));

    var sidenote = sidenote(step(Modification.CORRECTED, corrigendum), corrects);

    assertThat(sidenote.made()).isEqualTo("Corrected by Corrigendum 1 to 2019/103");
    assertThat(sidenote.dates())
        .extracting(SidenoteView.Applies::when)
        .containsExactly(
            "Corrigendum 1 to 2019/103 corrects 2019/103 from 31 December 2021 · first in the text"
                + " of 31 December 2021");
  }

  @Test
  void datesAChangeFromOneDayWithTheClauseThatSetsIt() {
    var sidenote =
        sidenote(
            step(Modification.REPLACED, A2017_815),
            from(A2017_815, LocalDate.of(2017, 6, 1), false, List.of()));

    assertThat(sidenote.made()).isEqualTo("Replaced by 2017/815");
    assertThat(sidenote.dates())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.when())
                  .isEqualTo("2017/815 · OJ L 122, 13.5.2017 · applies from 1 June 2017");
              assertThat(line.between()).isEqualTo(" · ");
              assertThat(line.clause()).extracting(Link::label).isEqualTo("Article 2 of 2017/815");
              assertThat(line.after()).isEmpty();
            });
  }

  @Test
  void datesAnActFromItsEntryIntoForceWithTheActsThatMovedItsDate() {
    var sidenote =
        sidenote(
            step(Modification.INSERTED, A2019_1583),
            from(A2019_1583, LocalDate.of(2021, 12, 31), true, List.of(A2020_910)));

    assertThat(sidenote.made()).isEqualTo("Inserted by 2019/1583");
    var line = sidenote.dates().getFirst();
    assertThat(line.when())
        .isEqualTo(
            "2019/1583 · OJ L 246, 26.9.2019 · applies from 31 December 2021, its entry into"
                + " force");
    assertThat(line.amendedBy())
        .containsExactly(
            new Link("2020/910", "https://eur-lex.europa.eu/eli/reg_impl/2020/910/oj/eng"));
  }

  @Test
  void pointsToTheClauseThatSetsTheDay() {
    var setBy =
        new Applicability.Applies(
            A2019_413, new Applying.SetBy(List.of()), List.of(article("3")), List.of());

    var line = sidenote(step(Modification.REPLACED, A2019_413), setBy).dates().getFirst();

    assertThat(line.when() + line.between() + Objects.requireNonNull(line.clause()).label())
        .isEqualTo(
            "2019/413 · OJ L 73, 15.3.2019 · applies from a date set in Article 3 of 2019/413");
  }

  @Test
  void chainsTheStepsOldestFirst() {
    var attribution =
        Attribution.inSteps(
            List.of(
                step(Modification.INSERTED, A2019_413),
                step(Modification.AMENDED, new ActIdentifier("32022R0421")),
                step(Modification.AMENDED, new ActIdentifier("32025R0920"))));

    assertThat(sidenote(attribution, null).made())
        .isEqualTo("Inserted by 2019/413, then amended by 2022/421, then amended by 2025/920");
  }

  @Test
  void namesTheActsOfOneStepTogether() {
    var attribution =
        Attribution.inSteps(List.of(step(Modification.REPLACED, A2019_103, A2019_1583)));

    assertThat(sidenote(attribution, null).made())
        .isEqualTo("Replaced by 2019/103 and 2019/1583 together");
    assertThat(sidenote(attribution, A2019_1583).made())
        .isEqualTo("Replaced by 2019/1583, together with 2019/103");
  }

  @Test
  void listsThreeActsOrMore() {
    var attribution =
        Attribution.inSteps(List.of(step(Modification.REPLACED, A2017_815, A2019_103, A2019_1583)));

    assertThat(sidenote(attribution, null).made())
        .isEqualTo("Replaced by 2017/815, 2019/103 and 2019/1583 together");
  }

  @Test
  void saysThatTheDataNamesNoAct() {
    var sidenote =
        SidenoteView.of(
            AVSEC,
            new Attributed(difference(Kind.CHANGED), Attribution.NONE),
            null,
            TODAY,
            Language.ENG);

    // The line is never empty. It says that no act is named.
    assertThat(sidenote.made()).isEqualTo("The data does not say which act amended this.");
    assertThat(sidenote.dates()).isEmpty();
  }

  @Test
  void namesTheActsOneOfWhichMadeIt() {
    var sidenote =
        SidenoteView.of(
            AVSEC,
            new Attributed(
                difference(Kind.CHANGED),
                Attribution.oneOf(List.of(A2019_103, A2019_1583, A2020_910))),
            null,
            TODAY,
            Language.ENG);

    assertThat(sidenote.made())
        .isEqualTo(
            "The data does not say which act amended this. It was 2019/103, 2019/1583 or"
                + " 2020/910.");
  }

  @Test
  void namesTheTextWhereAChangeFirstAppearsAndWhatItConsolidates() {
    var appeared =
        new Attributed(
            difference(Kind.DELETED),
            Attribution.NONE,
            List.of(),
            new Attributed.Appeared(
                version("02015R1998-20260322"),
                List.of(A2026_449, new ActIdentifier("32026R0449R(01)"))),
            null);

    var sidenote = SidenoteView.of(AVSEC, appeared, null, TODAY, Language.ENG);

    assertThat(sidenote.made())
        .isEqualTo(
            "Deleted in the text of 22 March 2026, which consolidates 2026/449 and Corrigendum 1"
                + " to 2026/449");
    assertThat(sidenote.dates()).isEmpty();
  }

  @Test
  void namesOnlyTheTextWhereItConsolidatesNoAct() {
    var appeared =
        new Attributed(
            difference(Kind.CHANGED),
            Attribution.NONE,
            List.of(),
            new Attributed.Appeared(version("02015R1998-20260322"), List.of()),
            null);

    assertThat(SidenoteView.of(AVSEC, appeared, null, TODAY, Language.ENG).made())
        .isEqualTo("Amended in the text of 22 March 2026");
  }

  @Test
  void saysAChangeDoesNotApplyYetInTheTenseOfTheDay() {
    var attributed =
        new Attributed(
            difference(Kind.CHANGED),
            Attribution.inSteps(List.of(step(Modification.REPLACED, A2026_449))));

    var later =
        SidenoteView.of(
            AVSEC,
            attributed.notApplyingYet(A2026_449, LocalDate.of(2026, 12, 1)),
            null,
            TODAY,
            Language.ENG);
    var earlier =
        SidenoteView.of(
            AVSEC,
            attributed.notApplyingYet(A2026_449, LocalDate.of(2026, 10, 1)),
            null,
            TODAY,
            Language.ENG);

    assertThat(later.prospective()).isEqualTo("2026/449 does not apply yet on 1 December 2026");
    assertThat(earlier.prospective()).isEqualTo("2026/449 did not apply yet on 1 October 2026");
    assertThat(SidenoteView.of(AVSEC, attributed, null, TODAY, Language.ENG).prospective())
        .isNull();
  }

  /** Returns the sidenote of a changed difference that the acts of {@code attribution} made. */
  private static SidenoteView sidenote(Attribution attribution, @Nullable ActIdentifier only) {
    return SidenoteView.of(
        AVSEC, new Attributed(difference(Kind.CHANGED), attribution), only, TODAY, Language.ENG);
  }

  /** Returns the sidenote of a changed difference that the act of {@code step} made. */
  private static SidenoteView sidenote(Attribution.Step step, Applicability applies) {
    return SidenoteView.of(
        AVSEC,
        new Attributed(
            difference(Kind.CHANGED),
            Attribution.inSteps(List.of(step)),
            List.of(applies),
            null,
            null),
        null,
        TODAY,
        Language.ENG);
  }

  private static Attribution.Step step(Modification modification, ActIdentifier... acts) {
    return new Attribution.Step(
        version("02015R1998-20260322"), Arrays.asList(acts), modification, Set.of());
  }

  /** Returns that {@code act} applies from {@code day}, as Article 2 of it sets. */
  private static Applicability from(
      ActIdentifier act, LocalDate day, boolean entryIntoForce, List<ActIdentifier> amendedBy) {
    return new Applicability.Applies(
        act, new Applying.From(day, entryIntoForce, List.of()), List.of(article("2")), amendedBy);
  }

  private static ActDate.Provision article(String number) {
    return new ActDate.Provision(ActDate.Provision.Type.ARTICLE, number);
  }

  private static Difference difference(Kind kind) {
    var eId = "anx/pnt_8.1.2.3/pnt_c";
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
        List.of(),
        List.of(),
        null);
  }

  private static Version version(String celex) {
    return AVSEC.timeline().versions().stream()
        .filter(v -> v.celex().equals(celex))
        .findFirst()
        .orElseThrow();
  }
}
