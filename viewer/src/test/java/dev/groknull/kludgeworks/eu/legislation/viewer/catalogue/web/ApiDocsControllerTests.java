// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests the page that documents the API, which puts together the parts that the modules give: the
 * catalogue's and the comparison's steps of the walk, link relations and problem types.
 */
@WebMvcTest(ApiDocsController.class)
@Import(ControllerTesting.class)
class ApiDocsControllerTests {
  @Autowired MockMvcTester mvc;

  private Document page;

  @BeforeEach
  void page() throws UnsupportedEncodingException {
    var result = mvc.get().uri("/api/docs").exchange();
    assertThat(result).hasStatusOk();
    page = Jsoup.parse(result.getResponse().getContentAsString());
  }

  @Test
  void walksFromAnActToAComparisonInTheOrderOfTheSteps() {
    assertThat(page.select(".api-guide > li > h3").eachText())
        .containsExactly("Find an act", "The law on a day", "Read the text", "Compare two days");
    assertThat(page.select(".api-guide figcaption").eachText())
        .first()
        .asString()
        .contains("GET /api/acts?celex=32015R1998", "303");
  }

  @Test
  void listsEveryStepInTheContentsByItsAnchor() {
    assertThat(page.select("nav a[href^=#start-]").eachAttr("href"))
        .containsExactly("#start-act", "#start-on", "#start-text", "#start-comparisons");
    assertThat(page.select(".api-guide > li").eachAttr("id"))
        .containsExactly("start-act", "start-on", "start-text", "start-comparisons");
  }

  @Test
  void showsEveryRelationOfEveryModuleWithTheCurieAsItsAnchor() {
    assertThat(page.select("#relations section").eachAttr("id"))
        .containsExactly(
            "act",
            "act-by-celex",
            "act-by-eli",
            "amendments",
            "comparisons",
            "difference",
            "events",
            "on",
            "outline",
            "preparation",
            "retry",
            "text",
            "unit",
            "version");
    assertThat(page.select("#act-by-eli h3").text()).isEqualTo("av:act-by-eli");
    assertThat(page.select("#act-by-eli p").text()).contains("ELI");
  }

  @Test
  void showsEveryProblemTypeWithItsNameAsItsAnchor() {
    assertThat(page.select("#problems section").eachAttr("id"))
        .containsExactly("before-first-version", "text-failed");
    assertThat(page.select("#before-first-version figcaption").text())
        .contains("/on/1999-01-01", "404");
    assertThat(page.select("#before-first-version dt").eachText()).contains("firstVersion");
  }

  @Test
  void showsTheEventsOfAPreparationWhereItTellsHowToWait() {
    assertThat(page.select("#answers-waiting + dd figcaption").text()).contains("/events");
  }

  @Test
  void listsEveryTermInTheContents() {
    assertThat(page.select("nav a[href=#difference]").text()).isEqualTo("av:difference");
    assertThat(page.select("nav a[href=#text-failed]").text()).isEqualTo("text-failed");
  }
}
