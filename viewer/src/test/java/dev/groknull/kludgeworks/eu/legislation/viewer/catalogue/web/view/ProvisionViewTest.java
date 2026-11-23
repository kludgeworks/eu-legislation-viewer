// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the words of the history of a provision of 2015/1998 on the Read page. The versions, the
 * dates and the changes are Cellar's, recorded. 2017/815 and 2019/103 each replaced point 4.1.3.1
 * of the Annex.
 */
class ProvisionViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
  static final String POINT = "anx/pnt_4.1.3.1";
  static final Version READING =
      AVSEC.timeline().startingOn(LocalDate.of(2026, 10, 1)).orElseThrow();
  static final String ARTICLE = "art_9";
  static final List<String> OCCURRENCES =
      List.of("anx", "anx/pnt_4", "anx/pnt_4.1", "anx/pnt_4.1.3", POINT, ARTICLE);

  private static ProvisionView history(String occurrence, Map<Version, TextTags> texts) {
    return ProvisionView.of(AVSEC, READING, occurrence, TODAY, TODAY, Language.ENG, texts);
  }

  @Test
  void listsTheActsThatChangedTheProvisionNewestFirstWithALinkToEachChange() {
    var view = history(POINT, Map.of(READING, new TextTags(OCCURRENCES, Map.of())));

    assertThat(view.heading()).startsWith("History of ").doesNotContain("this provision");
    assertThat(view.count()).endsWith(" amending acts changed it");
    var own = view.lines().stream().filter(line -> line.words().equals("replaced it.")).toList();
    assertThat(own).extracting(ProvisionView.Line::act).containsExactly("2019/103", "2017/815");
    var oldest = own.getLast();
    assertThat(oldest.href()).isEqualTo("/eli/reg_impl/2015/1998/eng/history/32017R0815");
    assertThat(oldest.change())
        .isEqualTo("/eli/reg_impl/2015/1998/eng/history/32017R0815#at-AN_PO_4.1.3.1");
    assertThat(oldest.statement()).startsWith("Applies from 1 June 2017");
    assertThat(oldest.tone()).isNull();
    assertThat(view.unknown()).isNull();
  }

  @Test
  void offersTheComparisonOfTheWordingIfTwoActsChangedTheProvision() {
    var view = history(POINT, Map.of(READING, new TextTags(OCCURRENCES, Map.of())));

    assertThat(view.compare())
        .startsWith("/eli/reg_impl/2015/1998/eng/compare?from=2016-02-29&to=2026-10-01")
        .endsWith("&occurrence=anx%2Fpnt_4.1.3.1");
  }

  @Test
  void theTenseObeysTheDayAndTheAddressKeepsTheDay() {
    var day = LocalDate.of(2017, 5, 1);
    var view =
        ProvisionView.of(
            AVSEC,
            READING,
            POINT,
            day,
            TODAY,
            Language.ENG,
            Map.of(READING, new TextTags(OCCURRENCES, Map.of())));

    var oldest =
        view.lines().stream()
            .filter(line -> line.act().equals("2017/815"))
            .findFirst()
            .orElseThrow();
    assertThat(oldest.statement()).isEqualTo("Was not in force yet.");
    assertThat(oldest.tone()).isEqualTo("not-in-force");
    assertThat(oldest.href()).endsWith("/history/32017R0815?on=2017-05-01");
  }

  @Test
  void anActThatOnlyTheTextTagsTheProvisionWithChangedIt() {
    var tagged = new ActIdentifier("32020R0910");
    var view =
        history(
            ARTICLE, Map.of(READING, new TextTags(OCCURRENCES, Map.of(tagged, Set.of(ARTICLE)))));

    assertThat(view.heading()).isEqualTo("History of this provision");
    assertThat(view.count()).isEqualTo("1 amending act changed it");
    assertThat(view.lines())
        .extracting(ProvisionView.Line::act, ProvisionView.Line::words, ProvisionView.Line::change)
        .containsExactly(tuple("2020/910", "changed it.", null));
    assertThat(view.compare()).isNull();
  }

  @Test
  void saysTheFirstTextWithAProvisionThatTheDataNamesNoActFor() {
    var first = AVSEC.timeline().versions().getFirst();
    var view =
        history(
            ARTICLE,
            Map.of(
                first,
                new TextTags(OCCURRENCES.subList(0, 5), Map.of()),
                READING,
                new TextTags(OCCURRENCES, Map.of())));

    assertThat(view.lines()).isEmpty();
    assertThat(view.count()).isNull();
    var unknown = view.unknown();
    assertThat(unknown).isNotNull();
    assertThat(unknown.words())
        .isEqualTo(
            "The data does not say which act inserted this provision. It first appears in the"
                + " text of 1 October 2026.");
    assertThat(unknown.link()).isEqualTo("The text of 1 October 2026");
    assertThat(unknown.href()).contains("CELEX:02015R1998-20261001");
  }

  @Test
  void theOccurrencesWithAHistoryToShowAreTheChangedOccurrencesAndTheOccurrencesWithNoAct() {
    var first = AVSEC.timeline().versions().getFirst();
    var occurrences =
        ProvisionView.occurrences(
            AVSEC,
            READING,
            Language.ENG,
            Map.of(
                first,
                new TextTags(OCCURRENCES.subList(0, 5), Map.of()),
                READING,
                new TextTags(OCCURRENCES, Map.of())));

    assertThat(occurrences).contains(POINT, ARTICLE);
  }
}
