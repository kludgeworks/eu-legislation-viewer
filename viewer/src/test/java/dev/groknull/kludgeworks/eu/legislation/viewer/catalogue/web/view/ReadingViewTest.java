// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the Reading card of {@link ReadingView} for the versions of 2015/1998. Today is 5 November
 * 2026. The versions are Cellar's, recorded.
 */
class ReadingViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  @Test
  void readsTheLawOnTheDayAskedByItsVersionAndTheActsThatMadeIt() {
    var card = card("2021-12-31", "2022-02-14", TODAY);
    assertThat(card.title()).isEqualTo("2015/1998 · the law on 14 Feb 2022");
    assertThat(card.day()).isEqualTo("14 February 2022");
    assertThat(card.version())
        .isEqualTo("From the text of 31 December 2021 · applied until 28 February 2022");
    assertThat(card.celex()).isEqualTo("02015R1998-20211231");
    assertThat(card.eurLex())
        .isEqualTo("https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:02015R1998-20211231");
    // Corrigendum 1 to 2019/103 corrects the German text only, so the English card leaves it out.
    assertThat(card.made()).isEqualTo("made by 2019/103, 2019/1583, 2020/910");
    assertThat(
            ReadingView.of(
                    AVSEC,
                    version("2021-12-31"),
                    LocalDate.parse("2021-12-31"),
                    Language.DEU,
                    TODAY,
                    List.of(),
                    Map.of())
                .made())
        .isEqualTo("made by 2019/103, 2019/1583, 2020/910 · also 1 corrigendum");
    assertThat(card.published())
        .startsWith(
            "2019/103: Commission Implementing Regulation (EU) 2019/103 of 23 January 2019 amending"
                + " Implementing Regulation (EU) 2015/1998")
        // The hover names the same acts as the line: not the German corrigendum.
        .contains(" · OJ L 21, 24.1.2019\n2019/1583: ")
        .endsWith("(Text with EEA relevance) · OJ L 208, 1.7.2020");
    assertThat(card.status().size()).isZero();
    assertThat(card.picker().single()).isEqualTo("/eli/reg_impl/2015/1998/eng");
    assertThat(card.picker().range()).isEqualTo("/eli/reg_impl/2015/1998/eng/compare");
    assertThat(card.picker().on()).isEqualTo("2022-02-14");
    assertThat(card.picker().from()).isNull();
  }

  @Test
  void saysANewVersionStartsOnItsFirstDayAndCallsTheOneThatAppliesTodayCurrent() {
    assertThat(card("2026-10-01", "2026-10-01", TODAY).version())
        .isEqualTo("A new text starts on this day · current text");
    assertThat(card("2026-03-22", "2026-04-02", LocalDate.of(2026, 5, 1)).version())
        .isEqualTo(
            "From the text of 22 March 2026 · applies until 30 September 2026 · current" + " text");
  }

  @Test
  void saysWhatMadeEachKindOfVersion() {
    assertThat(card("2015-11-15", "2015-11-15", TODAY).made())
        .isEqualTo("the act as published · OJ L 299, 14.11.2015");
    assertThat(card("2016-02-29", "2016-02-29", TODAY).made()).isEqualTo("made by 2015/2426");
    assertThat(card("2018-02-06", "2018-02-06", TODAY).made()).isEqualTo("made by 2018/55");
  }

  @Test
  void namesOnlyTheActsThatChangeThePagesLanguage() {
    // 2022/463 changes only the German text of 2015/1998. Its version of 12 April 2022 has the same
    // English wording as the text before.
    var april = version("2022-04-12");
    var english =
        ReadingView.of(AVSEC, april, april.date(), Language.ENG, TODAY, List.of(), Map.of());
    var german =
        ReadingView.of(AVSEC, april, april.date(), Language.DEU, TODAY, List.of(), Map.of());

    assertThat(english.made()).isEqualTo("Same wording in English as the text of 4 April 2022");
    assertThat(german.made()).isEqualTo("made by 2022/463");
  }

  @Test
  void namesTheActsAVersionsTextNamesWhereThePublicationsOfficeListsNone() {
    var actWithUnlisted =
        new Act(
            AVSEC.id(),
            AVSEC.eli(),
            AVSEC.type(),
            AVSEC.titles(),
            AVSEC.dateOfDocument(),
            AVSEC.inForce(),
            AVSEC.endOfValidity(),
            new Timeline(
                List.of(
                    AVSEC.timeline().versions().getFirst(),
                    new Version(
                        Version.Kind.CONSOLIDATED,
                        LocalDate.parse("2018-02-06"),
                        "02015R1998-20180206",
                        false,
                        List.of(),
                        AVSEC.timeline().versions().getFirst().languages(),
                        Map.of()))),
            AVSEC.dates(),
            AVSEC.amendments(),
            AVSEC.publications());
    var unlisted = actWithUnlisted.timeline().versions().getLast();
    var named =
        ReadingView.of(
            actWithUnlisted,
            unlisted,
            LocalDate.parse("2018-02-06"),
            Language.ENG,
            TODAY,
            List.of(new ActIdentifier("32017R0815")),
            Map.of());
    assertThat(named.made()).isEqualTo("made by 2017/815");
    assertThat(named.published()).startsWith("2017/815: Commission Implementing Regulation");
  }

  @Test
  void startsTheActAsPublishedOnItsEntryIntoForceAndSaysWhenItApplied() {
    var published = card("2015-11-15", "2015-11-15", TODAY);
    assertThat(published.version())
        .isEqualTo(
            "A new text starts on this day · in force from 15 November 2015 · applied 1"
                + " February 2016 to 28 February 2016");
  }

  @Test
  void callsTheVersionAfterTheCurrentOneTheNextVersion() {
    assertThat(card("2026-10-01", "2026-10-01", LocalDate.of(2026, 5, 1)).version())
        .isEqualTo("A new text starts on this day · next text");
  }

  @Test
  void warnsOfAPendingAmendmentOnlyOnTheDaysThatTheActAppliesAndTheTextLacksIt() {
    var pending = card("2016-02-29", "2017-06-03", TODAY);
    assertThat(pending.version())
        .isEqualTo("From the text of 29 February 2016 · applied until 6 June 2017");
    assertThat(pending.status().day()).isEqualTo("3 June 2017");
    assertThat(pending.status().lines())
        .containsExactly(
            new StatusView.Line(
                "2017/815",
                "/eli/reg_impl/2015/1998/eng/history/32017R0815?on=2017-06-03",
                "applied from 1 June 2017 · Article 2 of 2017/815. The text shows it from 7 June"
                    + " 2017."));
    assertThat(pending.picker().on()).isEqualTo("2017-06-03");
    assertThat(card("2016-02-29", "2017-05-01", TODAY).status().size()).isZero();
    assertThat(card("2016-02-29", "2016-02-29", TODAY).status().size()).isZero();
  }

  @Test
  void warnsOfAProspectiveTextWhileTheActIsInForceButDoesNotApply() {
    assertThat(card("2015-11-15", "2015-12-01", TODAY).status().lines())
        .containsExactly(
            new StatusView.Line(
                "2015/1998",
                null,
                "was in force and did not apply yet. It applied from 1 February 2016 · Article 2 of"
                    + " 2015/1998."));
    // The tense obeys the day: for the same day as today, the act "is in force".
    assertThat(
            card("2015-11-15", "2015-12-01", LocalDate.of(2015, 12, 1))
                .status()
                .lines()
                .getFirst()
                .words())
        .isEqualTo(
            "is in force and does not apply yet. It applies from 1 February 2016 · Article 2 of"
                + " 2015/1998.");
    assertThat(card("2015-11-15", "2016-02-01", TODAY).status().size()).isZero();
  }

  @Test
  void showsTheDayAndADashBeforeTheFirstVersion() {
    var before = ReadingView.before(AVSEC, LocalDate.parse("2015-01-01"), Language.ENG);
    assertThat(before.title()).isEqualTo("2015/1998 · the law on 1 Jan 2015");
    assertThat(before.day()).isEqualTo("1 January 2015");
    assertThat(before.version()).isNull();
    assertThat(before.made()).isNull();
    assertThat(before.celex()).isNull();
    assertThat(before.picker().on()).isEqualTo("2015-01-01");
  }

  /** The card of the version of {@code version}, asked for on {@code asked}, in English. */
  private static ReadingView card(String version, String asked, LocalDate today) {
    return ReadingView.of(
        AVSEC, version(version), LocalDate.parse(asked), Language.ENG, today, List.of(), Map.of());
  }

  private static Version version(String date) {
    return AVSEC.timeline().startingOn(LocalDate.parse(date)).orElseThrow();
  }
}
