// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import java.util.Objects;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Tests the controller of the home page alone, with {@code @WebMvcTest} and the modules mocked. The
 * tests check the "Find an act" box. A CELEX number or an ELI goes straight to the act. Words from
 * a title list the acts that have them. The acts and the search results come from {@link
 * OneActCellar}: 2015/1998, and 2026/449, which amends it.
 */
@WebMvcTest(HomeController.class)
@Import(ControllerTesting.class)
class HomeControllerTests {
  private static final String READ = "/eli/reg_impl/2015/1998/eng";
  private static final String COMPARE = READ + "/compare";

  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Search search;

  @BeforeEach
  void cellar() {
    when(acts.find(OneActCellar.CELEX)).thenReturn(Optional.of(OneActCellar.ACT));
    when(acts.find(OneActCellar.ELI)).thenReturn(Optional.of(OneActCellar.ACT));
    when(search.matching(any())).thenAnswer(asked -> OneActCellar.found(asked.getArgument(0)));
  }

  @Test
  void hasTheFindAnActBoxAndNoOtherInTheSiteBar() {
    var page = page(mvc.get().uri("/").exchange());

    assertThat(page.select("form.find-act input[name=q]").attr("placeholder"))
        .isEqualTo("Title words, CELEX number or ELI");
    assertThat(page.select("nav.site form.find")).isEmpty();
    assertThat(page.select(".results")).isEmpty();
    assertThat(page.select(".about h3").eachText())
        .containsExactly("What this is", "What it adds", "What it isn’t");
  }

  @Test
  void goesStraightToTheActForItsCelexNumber() {
    assertThat(mvc.get().uri("/?q=32015r1998"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, READ);
  }

  @Test
  void goesStraightToTheActForItsEli() {
    assertThat(mvc.get().uri("/?q=http://data.europa.eu/eli/reg_impl/2015/1998/oj"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, READ);
    assertThat(mvc.get().uri("/?q=eli/reg_impl/2015/1998"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, READ);
  }

  @Test
  void saysNoActHasACelexNumberCellarDoesntKnow() {
    // This is 185/2010. The stand-in Cellar knows only 2015/1998.
    assertThat(page(mvc.get().uri("/?q=32010R0185").exchange()).select(".none").text())
        .isEqualTo("No act has the CELEX number or ELI 32010R0185.");
  }

  @Test
  void listsTheActsWithTheWordsInTheirTitle() {
    var result = mvc.get().uri("/").param("q", "Aviation security").exchange();
    var page = page(result);

    assertThat(page.select(".results-head h2").text())
        .isEqualTo("Acts matching “Aviation security”");
    assertThat(page.select(".result .cx").stream().map(cx -> cx.ownText()))
        .containsExactly("32015R1998", "32026R0449");
    assertThat(page.select(".result .cx .k").eachText())
        .containsExactly("Implementing Regulation", "Amending act");
    var avsec = Objects.requireNonNull(page.selectFirst(".result .t"));
    assertThat(avsec.select("a").getFirst().text())
        .isEqualTo("Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015");
    assertThat(avsec.select("p").text())
        .isEqualTo(
            "laying down detailed measures for the implementation of the common basic standards"
                + " on aviation security (Text with EEA relevance)");
    assertThat(avsec.select("a").eachAttr("href")).containsOnly(READ);
    assertThat(avsec.select("a.to").text()).isEqualTo("Read it as it is today →");
    var amending = page.select(".result .t").get(1);
    assertThat(amending.select("a.to").text()).isEqualTo("What it changed in 2015/1998 →");
    assertThat(amending.select("a.to").attr("href")).isEqualTo(COMPARE + "?act=32026R0449");
    assertThat(page.select(".filters a").eachText())
        .containsExactly("All", "Regulations", "Directives", "Decisions", "Amending acts");
    assertThat(page.select(".filters a[aria-current=page]").text()).isEqualTo("All");
    assertThat(page.select(".filters a").get(4).attr("href"))
        .isEqualTo("/?q=Aviation+security&type=amending");
    assertThat(page.select(".more").text()).isEqualTo("Acts 1–2, closest match first");
    assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
        .singleElement()
        .asString()
        .startsWith("search=q=Aviation+security;");
  }

  @Test
  void findsNoKindOfActThereIsnt() {
    assertThat(mvc.get().uri("/?q=aviation&type=treaty")).hasStatus(HttpStatus.NOT_FOUND);
  }

  static Document page(MvcTestResult result) {
    assertThat(result).hasStatusOk();
    try {
      return Jsoup.parse(result.getResponse().getContentAsString());
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }
}
