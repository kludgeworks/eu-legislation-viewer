// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextMarks;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests the history of an act and the page of an amending act, with {@code @WebMvcTest} and the
 * modules mocked. Today is 1 June 2021. The history is of the act of {@link OneActCellar}. The page
 * of an amending act is of a made-up act with changes in two groups.
 */
@WebMvcTest(HistoryController.class)
@Import({ControllerTesting.class, HistoryControllerTests.OnTheFirstOfJune2021.class})
class HistoryControllerTests {
  private static final String HISTORY = "/eli/reg_impl/2015/1998/eng/history";

  private static final Eli PHASED_ELI = new Eli("reg/2018/1862");
  private static final ActIdentifier PHASED_BASE = new ActIdentifier("32018R1862");
  private static final ActIdentifier PHASED = new ActIdentifier("32022R1190");
  private static final LocalDate IN_PART = LocalDate.of(2022, 8, 1);
  private static final LocalDate IN_FULL = LocalDate.of(2026, 10, 15);
  private static final String AMENDING = "/eli/reg/2018/1862/eng/history/32022R1190";

  private static final Version ORIGINAL =
      Version.original(
          LocalDate.of(2018, 12, 27), PHASED_BASE, EnumSet.allOf(Language.class), Map.of());
  private static final Version WITH_PHASED =
      new Version(
          Version.Kind.CONSOLIDATED,
          IN_PART,
          "02018R1862-20220801",
          false,
          List.of(PHASED),
          EnumSet.allOf(Language.class),
          Map.of());

  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;

  @BeforeEach
  void acts() {
    when(acts.find(OneActCellar.ELI)).thenReturn(Optional.of(OneActCellar.ACT));
    when(acts.find(PHASED_ELI)).thenReturn(Optional.of(phased()));
    when(texts.preparation(any(), any()))
        .thenAnswer(asked -> ControllerTesting.preparing(asked.getArgument(1)));
    when(texts.marks(any())).thenReturn(Map.of());
  }

  @Test
  void listsTheAmendingActsNewestFirstWithTheStateOfEachOnToday() {
    var page = page(HISTORY);
    assertThat(page.title()).isEqualTo("2015/1998 · history on 1 Jun 2021");
    assertThat(page.select(".status").text())
        .startsWith(
            "On 1 June 2021. 2015/1998 applies. It applies from 1 February 2016 · Article 2 of"
                + " 2015/1998.");
    assertThat(page.select("table.acts .cited").eachText())
        .containsExactly("2026/449", "2020/910", "2019/1583", "2021/255", "2019/413", "2019/103");
    assertThat(page.select("table.acts .state").eachText())
        .containsExactly(
            "Not in force yet", "No dates", "No dates", "No dates", "In force", "In force");
    assertThat(page.select("table.acts tbody tr").get(4).select("td p").text())
        .isEqualTo(
            "Article 3 of 2019/413 sets the day from which it applies. The viewer has no date for"
                + " that day.");
    assertThat(page.select("table.acts tbody tr").get(5).select("td p").text())
        .isEqualTo(
            "Applies in parts from 1 February 2019 and 31 December 2021 · Article 2 of 2019/103");
    assertThat(page.select("nav.mode [aria-current]").text()).isEqualTo("Amending acts (6)");
  }

  @Test
  void usesThePastTenseForADayInThePast() {
    var page = page(HISTORY + "?on=2020-01-01");
    assertThat(page.select(".status").text()).startsWith("On 1 January 2020. 2015/1998 applied.");
    assertThat(page.select("table.acts .state").eachText()).contains("Was in force");
    assertThat(page.select("table.acts tbody a[href*=history]").eachAttr("href").getFirst())
        .isEqualTo("/eli/reg_impl/2015/1998/eng/history/32026R0449?on=2020-01-01");
  }

  @Test
  void listsTheAmendingActsBetweenTwoDaysAndOpensTheirComparison() {
    // The later day comes first in the address: the page puts the days in order.
    var page = page(HISTORY + "?from=2021-06-01&to=2019-01-01");
    assertThat(page.title()).isEqualTo("2015/1998 · 1 Jan 2019 → 1 Jun 2021");
    assertThat(page.select(".between p").text())
        .isEqualTo(
            "From 1 January 2019 to 1 June 2021. 5 amending acts changed 2015/1998 between these"
                + " days.");
    assertThat(page.select("table.acts .cited").eachText())
        .containsExactly("2020/910", "2019/1583", "2021/255", "2019/413", "2019/103");
    assertThat(page.select("table.acts thead th").get(1).text()).isEqualTo("State on 1 June 2021");
    assertThat(page.select(".between a.button").attr("href"))
        .isEqualTo("/eli/reg_impl/2015/1998/eng/compare?from=2019-01-01&to=2021-06-01");
    assertThat(page.select(".history-head a").text()).isEqualTo("Show all 6");
    assertThat(page.select(".history-head a").attr("href")).isEqualTo(HISTORY);
    assertThat(page.select("day-picker input[type=date]").eachAttr("name"))
        .containsExactly("from", "to");
    assertThat(page.select("day-picker").attr("data-range")).isEqualTo(HISTORY);
  }

  @Test
  void hasOneDateBoxAndTheDaysWithAChangeForTheCalendar() {
    var page = page(HISTORY);
    assertThat(page.select("day-picker input[type=date]").eachAttr("name")).containsExactly("on");
    assertThat(page.select("day-picker").attr("data-single")).isEqualTo(HISTORY);
    assertThat(page.select("day-picker").attr("data-changes"))
        .isEqualTo(
            "2019-02-01=A part of 2019/103 applies from this day;"
                + "2021-12-31=A part of 2019/103 applies from this day;"
                + "2026-03-22=A part of 2026/449 applies from this day;"
                + "2026-10-01=A part of 2026/449 applies from this day");
    // The page of an amending act takes one day only.
    assertThat(page(HISTORY + "/32019R0413").select("day-picker").hasAttr("data-range")).isFalse();
  }

  @Test
  void showsTheGroupsOfAnAmendingActAndMarksTheChangesThatDoNotApplyYet() {
    var page = page(AMENDING + "?on=2022-08-01");
    assertThat(page.select("h2").eachText().getFirst())
        .isEqualTo("What 2022/1190 changed in 2018/1862");
    assertThat(page.select(".status").text())
        .startsWith("On 1 August 2022. 2022/1190 applies in part. It changed 3 provisions.");
    assertThat(page.select("a.group").eachText())
        .containsExactly(
            "All changes3",
            "Applies from 1 August 20221",
            "Does not apply yet, from 15 October 20262");
    assertThat(page.select("a.group.open")).isEmpty();
    assertThat(page.select("a.group.warn")).hasSize(1);
    assertThat(page.select("section.run .note").eachText())
        .containsExactly(
            "Applies from 1 August 2022. 1 provision",
            "Does not apply yet. Applies from 15 October 2026. 2 provisions");
    assertThat(page.select("section.run.warn .place").eachText())
        .containsExactly("Article 3, point 8", "Article 20(1)");
    assertThat(page.select("section.run:not(.warn) .place").eachText())
        .containsExactly("Article 79(7)");
  }

  @Test
  void linksTheArticleThatSetsTheDayAndShowsNoGroupsWhereTheChangesAreInOne() {
    var page = page(HISTORY + "/32019R0413");
    assertThat(page.select(".status").text())
        .contains("Article 3 of 2019/413 sets the day from which it applies.");
    assertThat(page.select(".status a[rel=external]").text()).startsWith("Read Article 3");
    assertThat(page.select(".status a[rel=external]").attr("href")).endsWith("#art_3");
    assertThat(page.select("a.group")).isEmpty();
    assertThat(page.select("section.run .note")).isEmpty();
  }

  @Test
  void limitsThePageOfAnAmendingActToOneGroup() {
    var page = page(AMENDING + "?on=2022-08-01&applies=2022-08-01");
    assertThat(page.select("ul.changes-list .place").eachText()).containsExactly("Article 79(7)");
    assertThat(page.select("a.group[aria-current]").text())
        .startsWith("Applies from 1 August 2022");
  }

  @Test
  void showsAChangeByItsNameOnlyWhileTheTextsAreNotBuilt() {
    var page = page(AMENDING + "?on=2022-08-01");
    assertThat(page.select("details.wording")).isEmpty();
  }

  @Test
  void loadsTheWordingOfEachChangeFromTheComparisonOfTheTextBeforeTheActAndTheFirstTextWithIt() {
    when(texts.marks(any()))
        .thenReturn(
            Map.of(
                ORIGINAL,
                new TextMarks(List.of("art_3", "art_3/pnt_8", "art_20", "art_20/par_1"), Map.of()),
                WITH_PHASED,
                new TextMarks(
                    List.of("art_3", "art_3/pnt_8", "art_79", "art_79/par_7"), Map.of())));

    var page = page(AMENDING + "?on=2022-08-01");

    var compare = "/eli/reg/2018/1862/eng/compare/place?from=2018-12-27&to=2022-08-01";
    // Article 20(1) is only in the text before the act: the act deleted it.
    assertThat(page.select("details.wording").eachAttr("hx-get"))
        .containsExactly(
            compare + "&act=32022R1190&eId=art_3%2Fpnt_8",
            compare + "&act=32022R1190&eId=art_79%2Fpar_7",
            compare + "&act=32022R1190&eId=art_20%2Fpar_1");
    // 2022/1190 is the only act of its version: no change can be of another act.
    assertThat(page.select("[hx-get*=/compare/either]")).isEmpty();
    // The page has few changes, so each wording is open and loads with the page.
    assertThat(page.select("details.wording[open]")).hasSize(3);
    assertThat(page.select("details.wording").eachAttr("hx-trigger")).containsOnly("load");
  }

  @Test
  void findsNoPageForAnActThatDoesNotAmendTheAct() {
    assertThat(mvc.get().uri(HISTORY + "/32022R1190").accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  private Document page(String uri) {
    var result = mvc.get().uri(uri).accept(MediaType.TEXT_HTML).exchange();
    assertThat(result).hasStatusOk();
    try {
      return Jsoup.parse(result.getResponse().getContentAsString());
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A made-up act with one amending act. The amending act enters into force and applies in part on
   * 1 August 2022, and applies in full from 15 October 2026. One of its changes is in the first
   * group, and two are in the second group.
   */
  private static Act phased() {
    return new Act(
        PHASED_BASE,
        PHASED_ELI,
        new ResourceType("REG"),
        Map.of(),
        null,
        true,
        null,
        new Timeline(List.of(ORIGINAL, WITH_PHASED)),
        List.of(),
        Map.of(
            PHASED,
            List.of(
                date(IN_PART, ActDate.Kind.ENTRY_INTO_FORCE, false),
                date(IN_PART, ActDate.Kind.APPLICATION, true),
                date(IN_FULL, ActDate.Kind.APPLICATION, false))),
        List.of(
            change("AR 79 PA 7", IN_PART),
            change("AR 3 PO 8", IN_FULL),
            change("AR 20 PA 1", IN_FULL)),
        Map.of());
  }

  private static ActDate date(LocalDate day, ActDate.Kind kind, boolean partial) {
    return new ActDate(
        day, kind, partial, new ActDate.Clause(null, null, List.of(), List.of(), ""));
  }

  private static Amendment change(String place, LocalDate from) {
    return new Amendment(
        PHASED, new Location(place), Amendment.Role.REPLACEMENT, from, null, Set.of());
  }

  /** Sets today for the pages to 1 June 2021. */
  @TestConfiguration(proxyBeanMethods = false)
  static class OnTheFirstOfJune2021 {
    @Bean
    @SuppressWarnings("TimeZoneUsage") // a fixed stand-in for the app's Clock bean
    Clock firstOfJune2021() {
      return Clock.fixed(Instant.parse("2021-06-01T08:00:00Z"), ZoneOffset.UTC);
    }
  }
}
