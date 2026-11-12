// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
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
 * out. The comparison's parts of the page are the comparison module's, and its tests test them.
 */
@WebMvcTest(CompareController.class)
@Import({ControllerTesting.class, CompareControllerTests.OnTheSixthOfNovember2026.class})
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
  void opensAnActsChangesOnTheVersionThatFirstIntegratesIt() {
    assertThat(mvc.get().uri(ACT + "eng/compare?act=32026R0449"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(
            HttpHeaders.LOCATION, ACT + "eng/compare?from=2021-12-31&to=2026-03-22&act=32026R0449");
  }

  @Test
  void goesToTheVersionsTheDatesAskForEarlierFirst() {
    assertThat(mvc.get().uri(ACT + "eng/compare?from=2026-12-01&to=2021-06-01"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, ACT + "eng/compare?from=2021-03-15&to=2026-10-01");
  }

  @Test
  void goesToTheComparisonOfAllActsForAnActNotBetweenTheVersionsSayingSo() {
    assertThat(mvc.get().uri(PAGE + "&act=32019R0103"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, PAGE + "&outside=32019R0103");
  }

  @Test
  void findsNoVersionBeforeTheActsTextApplies() {
    assertThat(
            mvc.get()
                .uri(ACT + "eng/compare?from=2000-01-01&to=2026-10-01")
                .accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void keepsTheViewItIsAskedForWhenItGoesToTheCanonicalAddress() {
    assertThat(mvc.get().uri(ACT + "eng/compare?from=2026-04-01&to=2026-10-01&view=whole"))
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
  static class OnTheSixthOfNovember2026 {
    @Bean
    @SuppressWarnings("TimeZoneUsage") // a fixed stand-in for the app's Clock bean
    Clock sixthOfNovember2026() {
      return Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), ZoneOffset.UTC);
    }
  }
}
