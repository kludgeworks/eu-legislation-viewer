// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

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
        .isEqualTo("From the version of 31 December 2021 · applied until 28 February 2022");
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
                    List.of())
                .made())
        .isEqualTo("made by 2019/103, 2019/1583, 2020/910 · also 1 corrigendum");
    assertThat(card.published())
        .startsWith(
            "2019/103: Commission Implementing Regulation (EU) 2019/103 of 23 January 2019 amending"
                + " Implementing Regulation (EU) 2015/1998")
        .contains(
            " · OJ L 21, 24.1.2019\nCorrigendum 1 to 2019/103 · OJ L 397, 26.11.2020\n2019/1583: ")
        .endsWith("(Text with EEA relevance) · OJ L 208, 1.7.2020");
    assertThat(card.notes()).isEmpty();
    assertThat(card.lags()).isEmpty();
    assertThat(card.action()).isEqualTo("/eli/reg_impl/2015/1998/eng");
    assertThat(card.on()).isEqualTo("2022-02-14");
    assertThat(card.starts()).startsWith("2015-11-15 2016-02-29 ").endsWith(" 2026-10-01");
  }

  @Test
  void saysANewVersionStartsOnItsFirstDayAndCallsTheOneThatAppliesTodayCurrent() {
    assertThat(card("2026-10-01", "2026-10-01", TODAY).version())
        .isEqualTo("A new version starts on this day · current version");
    assertThat(card("2026-03-22", "2026-04-02", LocalDate.of(2026, 5, 1)).version())
        .isEqualTo(
            "From the version of 22 March 2026 · applies until 30 September 2026 · current"
                + " version");
  }

  @Test
  void saysWhatMadeEachKindOfVersion() {
    assertThat(card("2015-11-15", "2015-11-15", TODAY).made())
        .isEqualTo("the act as published · OJ L 299, 14.11.2015");
    assertThat(card("2016-02-29", "2016-02-29", TODAY).made()).isEqualTo("made by 2015/2426");
    assertThat(card("2018-02-06", "2018-02-06", TODAY).made()).isEqualTo("made by 2018/55");
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
            AVSEC.amendingDates(),
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
            List.of(new ActIdentifier("32017R0815")));
    assertThat(named.made()).isEqualTo("made by 2017/815");
    assertThat(named.published()).startsWith("2017/815: Commission Implementing Regulation");
  }

  @Test
  void startsTheActAsPublishedOnItsEntryIntoForceAndSaysWhenItApplied() {
    var published = card("2015-11-15", "2015-11-15", TODAY);
    assertThat(published.version())
        .isEqualTo(
            "A new version starts on this day · in force from 15 November 2015 · applied 1"
                + " February 2016 to 28 February 2016");
    assertThat(published.compare())
        .isEqualTo("/eli/reg_impl/2015/1998/eng/compare?from=2015-11-15&to=2016-02-29");
    assertThat(published.comparing()).isEqualTo("Compare with the version after");
    assertThat(card("2015-11-15", "2016-01-10", TODAY).notes())
        .singleElement(STRING)
        .startsWith(
            "On 10 January 2016 the act was in force but didn’t apply yet: it applies from 1"
                + " February 2016");
    assertThat(card("2016-02-29", "2016-02-29", TODAY).comparing())
        .isEqualTo("Compare with the version before");
  }

  @Test
  void callsTheVersionAfterTheCurrentOneTheNextVersion() {
    assertThat(card("2026-10-01", "2026-10-01", LocalDate.of(2026, 5, 1)).version())
        .isEqualTo("A new version starts on this day · next version");
  }

  @Test
  void saysNothingOfALagLaterInTheVersionsPeriod() {
    assertThat(card("2016-02-29", "2016-02-29", TODAY).lags()).isEmpty();
  }

  @Test
  void warnsOnADateAskedOnlyWhenTheTextLagsThenItself() {
    var lagging = card("2016-02-29", "2017-06-03", TODAY);
    assertThat(lagging.version())
        .isEqualTo("From the version of 29 February 2016 · applied until 6 June 2017");
    assertThat(lagging.lags())
        .containsExactly(
            "On 3 June 2017, 2017/815 already applied; this text doesn’t include it yet (it does"
                + " from 7 June 2017).");
    assertThat(lagging.on()).isEqualTo("2017-06-03");
    assertThat(card("2016-02-29", "2017-05-01", TODAY).lags()).isEmpty();
  }

  @Test
  void showsTheDayAndADashBeforeTheFirstVersion() {
    var before = ReadingView.before(AVSEC, LocalDate.parse("2015-01-01"), Language.ENG);
    assertThat(before.title()).isEqualTo("2015/1998 · the law on 1 Jan 2015");
    assertThat(before.day()).isEqualTo("1 January 2015");
    assertThat(before.version()).isNull();
    assertThat(before.made()).isNull();
    assertThat(before.celex()).isNull();
    assertThat(before.on()).isEqualTo("2015-01-01");
    assertThat(before.starts()).startsWith("2015-11-15 ");
  }

  /** The card of the version of {@code version}, asked for on {@code asked}, in English. */
  private static ReadingView card(String version, String asked, LocalDate today) {
    return ReadingView.of(
        AVSEC, version(version), LocalDate.parse(asked), Language.ENG, today, List.of());
  }

  private static Version version(String date) {
    return AVSEC.timeline().startingOn(LocalDate.parse(date)).orElseThrow();
  }
}
