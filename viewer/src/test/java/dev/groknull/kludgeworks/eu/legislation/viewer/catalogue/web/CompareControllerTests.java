// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.BrusselsToday;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.CompareService;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.HistoryService;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.LawService;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests the controller of the compare page alone, with {@code @WebMvcTest} and the catalogue
 * mocked. The tests check where its addresses lead and what it refuses. The act is the act of
 * {@link OneActCellar}, with nothing of it prepared yet. The tests also check the way back to the
 * last search in the site bar. {@code ComparePagesTests} tests the page as the whole app works it
 * out. The comparison's fragments of the page are the comparison module's, and its tests test them.
 */
@WebMvcTest(CompareController.class)
@Import({
  ControllerTesting.class,
  HistoryService.class,
  LawService.class,
  CompareService.class,
  CompareControllerTests.OnTheSixthOfNovember2026.class
})
class CompareControllerTests {
  private static final String ACT = "/eli/reg_impl/2015/1998/";
  private static final String PAGE = ACT + "eng/compare?from=2026-03-22&to=2026-10-01";

  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;

  @BeforeEach
  void act() {
    when(acts.find(OneActCellar.ELI)).thenReturn(Optional.of(OneActCellar.ACT));
    when(texts.prepare(any(), any()))
        .thenAnswer(asked -> ControllerTesting.preparing(asked.getArgument(1)));
  }

  @Test
  void opensOnTheCurrentVersionAgainstTheOneBefore() {
    assertThat(mvc.get().uri(ACT + "eng/compare"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, PAGE);
  }

  @Test
  void keepsTheDaysAskedForEarlierFirst() {
    assertThat(mvc.get().uri(ACT + "eng/compare?from=2026-12-01&to=2021-06-01"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, ACT + "eng/compare?from=2021-06-01&to=2026-12-01");
    assertThat(mvc.get().uri(ACT + "eng/compare?from=2021-06-01&to=2026-12-01")).hasStatusOk();
  }

  @Test
  void keepsTheProvisionThatThePageIsLimitedTo() {
    assertThat(
            mvc.get().uri(ACT + "eng/compare?from=2026-12-01&to=2021-06-01&occurrence=anx/pnt_1"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(
            HttpHeaders.LOCATION,
            ACT + "eng/compare?from=2021-06-01&to=2026-12-01&occurrence=anx%2Fpnt_1");
  }

  @Test
  void saysTheActDidntExistYetWhenFromIsBeforeItWasAdopted() throws UnsupportedEncodingException {
    var result =
        mvc.get()
            .uri(ACT + "eng/compare?from=2000-01-01&to=2026-10-01")
            .accept(MediaType.TEXT_HTML)
            .exchange();
    assertThat(result).hasStatusOk();
    var page = Jsoup.parse(result.getResponse().getContentAsString());
    assertThat(page.title()).isEqualTo("2015/1998 · 1 Jan 2000 → 1 Oct 2026");
    assertThat(page.select("#compare-head").text()).isEqualTo("2015/1998 on two days");
    assertThat(page.select("day-picker input[type=date]").eachAttr("value"))
        .containsExactly("2000-01-01", "2026-10-01");
    assertThat(page.select("section.compare p b").text())
        .isEqualTo("From 1 January 2000 to 1 October 2026.");
    // No tab opens Compare.
    assertThat(page.select("nav.mode").text()).doesNotContain("Compare");
    assertThat(page.select("#failure").text())
        .isEqualTo("2015/1998 didn’t exist yet on 1 January 2000");
    assertThat(page.select(".failure-actions a.button").eachText())
        .containsExactly("Compare from 30 November 2015");
  }

  @Test
  void keepsTheViewItIsAskedForWhenItGoesToTheCanonicalAddress() {
    assertThat(mvc.get().uri(ACT + "eng/compare?from=2026-10-01&to=2026-03-22&view=whole"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, PAGE + "&view=whole");
  }

  @Test
  void linksBackToTheLastSearchResultsFromTheSiteBar() throws UnsupportedEncodingException {
    var result =
        mvc.get()
            .uri(PAGE)
            .cookie(new Cookie("search", "q=aviation+security&type=regulation"))
            .exchange();
    assertThat(result).hasStatusOk();
    var page = Jsoup.parse(result.getResponse().getContentAsString());

    var back = Objects.requireNonNull(page.selectFirst("nav.site a.results"));
    assertThat(back.text()).isEqualTo("‹ Results for “aviation security”");
    assertThat(back.attr("href")).isEqualTo("/?q=aviation%20security&type=regulation");
    assertThat(page.select("nav.site form.find input[name=q]")).hasSize(1);
  }

  @TestConfiguration(proxyBeanMethods = false)
  @Import(BrusselsToday.class)
  static class OnTheSixthOfNovember2026 {
    @Bean
    @SuppressWarnings("TimeZoneUsage") // a fixed stand-in for the app's Clock bean
    Clock sixthOfNovember2026() {
      return Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), ZoneOffset.UTC);
    }
  }
}
