// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.List;
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
 * catalogue's and the comparison's resources and problem types.
 */
@WebMvcTest(ApiDocsController.class)
@Import({ControllerTesting.class, ApiDocs.class})
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
  void showsEveryResourceOfEveryModuleInTheOrderOfItsName() {
    assertThat(page.select("#resources > section").eachAttr("id"))
        .containsExactly(
            "root",
            "act",
            "version",
            "on",
            "amendments",
            "text",
            "outline",
            "unit",
            "preparation",
            "comparison",
            "difference");
    assertThat(page.select("#resources > section > h3").eachText())
        .startsWith("Root", "Act", "Version", "The law on a day");
    assertThat(page.select("#root figcaption").text())
        .contains("GET /api/acts?celex=32015R1998", "303");
  }

  @Test
  void listsEveryResourceInTheContentsByItsAnchor() {
    assertThat(page.select("nav a[href=#resources] + ul a").eachAttr("href"))
        .containsExactlyElementsOf(
            page.select("#resources > section").eachAttr("id").stream()
                .map(id -> "#" + id)
                .toList());
    assertThat(page.select("nav a[href=/about#what]").text()).isEqualTo("What this is");
  }

  @Test
  void hasAnAnchorInTheResourcesForEveryRelationOfEveryModule() {
    assertThat(
            List.of(
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
                "version"))
        .allSatisfy(
            relation -> assertThat(page.select("#resources #" + relation)).as(relation).hasSize(1));
    assertThat(page.select("#act-by-eli").text()).isEqualTo("av:act-by-eli");
    assertThat(page.select("#act-by-eli + dd").text()).contains("ELI");
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
    assertThat(page.select("#waiting + dd figcaption").text()).contains("/events");
  }

  @Test
  void listsEveryProblemTypeInTheContents() {
    assertThat(page.select("nav a[href=#text-failed]").text()).isEqualTo("text-failed");
  }
}
