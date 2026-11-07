// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Reading card of 2015/1998's versions, as Cellar has them (recorded), on 5 November 2026. */
class ReadingViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  @Test
  void readsAVersionByItsPeriodAndTheActsThatMadeIt() {
    var card = card("2021-12-31", "2021-12-31", TODAY);
    assertThat(card.celex()).isEqualTo("02015R1998-20211231");
    assertThat(card.eurLex())
        .isEqualTo("https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:02015R1998-20211231");
    assertThat(card.period()).isEqualTo("Applied 31 December 2021 to 28 February 2022");
    assertThat(card.made()).isEqualTo("made by 2019/103, 2019/1583, 2020/910 · also 1 corrigendum");
    assertThat(card.published())
        .startsWith(
            "2019/103: Commission Implementing Regulation (EU) 2019/103 of 23 January 2019 amending"
                + " Implementing Regulation (EU) 2015/1998")
        .contains(
            " · OJ L 21, 24.1.2019\ncorrigendum 1 to 2019/103 · OJ L 397, 26.11.2020\n2019/1583: ")
        .endsWith("(Text with EEA relevance) · OJ L 208, 1.7.2020");
    assertThat(card.asked()).isNull();
    assertThat(card.lags()).isEmpty();
    assertThat(card.action()).isEqualTo("/eli/reg_impl/2015/1998/eng");
    assertThat(card.on()).isEqualTo("2021-12-31");
    assertThat(card.first()).isEqualTo("2015-11-15");
  }

  @Test
  void callsTheVersionThatAppliesTodayCurrentAndAnInterimOneRevisable() {
    assertThat(card("2026-10-01", "2026-10-01", TODAY).period())
        .isEqualTo("Applies from 1 October 2026 · current version · may still be revised");
    assertThat(card("2026-03-22", "2026-03-22", LocalDate.of(2026, 5, 1)).period())
        .isEqualTo("Applies 22 March 2026 to 30 September 2026 · current version");
  }

  @Test
  void saysWhatMadeEachKindOfVersion() {
    assertThat(card("2015-11-15", "2015-11-15", TODAY).made())
        .isEqualTo("the act as published · OJ L 299, 14.11.2015");
    assertThat(card("2016-02-29", "2016-02-29", TODAY).made())
        .isEqualTo("made by 2015/2426 · also 4 corrigenda");
    assertThat(card("2018-02-06", "2018-02-06", TODAY).made())
        .isEqualTo("no amending act listed for this version");
  }

  @Test
  void namesTheActsAVersionsTextNamesWhereThePublicationsOfficeListsNone() {
    var named =
        ReadingView.of(
            AVSEC,
            version("2018-02-06"),
            LocalDate.parse("2018-02-06"),
            Language.ENG,
            TODAY,
            List.of(new ActIdentifier("32017R0815")));
    assertThat(named.made())
        .isEqualTo(
            "made by 2017/815 · named in the consolidated text; not in the Publications Office’s"
                + " list");
    assertThat(named.published()).startsWith("2017/815: Commission Implementing Regulation");
  }

  @Test
  void warnsWhereTheVersionsTextLagsTheLaw() {
    assertThat(card("2016-02-29", "2016-02-29", TODAY).lags())
        .containsExactly(
            "From 1 June 2017 this text lags the law: 2017/815 already applied, and no version"
                + " includes it until 7 June 2017.");
  }

  @Test
  void warnsOnADateAskedOnlyWhenTheTextLagsThenItself() {
    var lagging = card("2016-02-29", "2017-06-03", TODAY);
    assertThat(lagging.asked()).isEqualTo("The version that applied on 3 June 2017");
    assertThat(lagging.lags())
        .containsExactly(
            "On this date, 2017/815 already applied (from 1 June 2017, Article 2 of 2017/815). No"
                + " consolidated version includes it until 7 June 2017.");
    assertThat(lagging.on()).isEqualTo("2017-06-03");
    assertThat(card("2016-02-29", "2017-05-01", TODAY).lags()).isEmpty();
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
