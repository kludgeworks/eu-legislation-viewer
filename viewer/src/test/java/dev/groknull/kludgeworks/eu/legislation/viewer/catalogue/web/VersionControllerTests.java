// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
 * The version's page's controller alone (@WebMvcTest), the modules mocked: where its addresses lead
 * and what it refuses, for OneActCellar's act, nothing of it prepared yet; and the page's chrome,
 * its theme and assets. The pages as the whole app builds them are VersionPagesTests'.
 */
@WebMvcTest(VersionController.class)
@Import({ControllerTesting.class, VersionControllerTests.OnTheFirstOfJune2021.class})
class VersionControllerTests {
  private static final String PAGE = "/eli/reg_impl/2015/1998/2026-03-22/eng";

  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;
  @MockitoBean Content content;

  @BeforeEach
  void act() {
    when(acts.find(OneActCellar.ELI)).thenReturn(Optional.of(OneActCellar.ACT));
    when(texts.prepare(any(), any()))
        .thenAnswer(asked -> ControllerTesting.preparing(asked.getArgument(1)));
  }

  @Test
  void opensAnActAtTheVersionThatAppliesToday() {
    assertThat(mvc.get().uri("/eli/reg_impl/2015/1998/eng"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, "/eli/reg_impl/2015/1998/2021-03-15/eng");
  }

  @Test
  void opensAnActOnTheDateTheReaderAsks() {
    assertThat(mvc.get().uri("/eli/reg_impl/2015/1998/fra?on=2022-05-01"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, "/eli/reg_impl/2015/1998/2022-05-01/fra");
  }

  @Test
  void findsNoVersionBeforeTheActsTextApplies() {
    assertThat(mvc.get().uri("/eli/reg_impl/2015/1998/2000-01-01/eng").accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void findsNoPageInALanguageTheEuDoesntHave() {
    assertThat(mvc.get().uri("/eli/reg_impl/2015/1998/2026-03-22/xxx").accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void triesAgainThenGoesBackToThePage() {
    assertThat(mvc.post().uri(PAGE))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, PAGE);
    verify(texts).retry(OneActCellar.ACT, Language.ENG);
  }

  @Test
  void followsTheDeviceUntilTheReaderChoosesATheme() {
    assertThat(page(mvc.get().uri(PAGE)).root().child(0).attr("data-theme")).isEqualTo("system");
    var dark = page(mvc.get().uri(PAGE).cookie(new jakarta.servlet.http.Cookie("theme", "dark")));
    assertThat(dark.root().child(0).attr("data-theme")).isEqualTo("dark");
    assertThat(dark.select("form.theme [aria-pressed=true]"))
        .extracting(button -> button.attr("value"))
        .containsExactly("dark");
  }

  @Test
  void linksItsStylesheetAndScriptsByTheirContent() {
    var assets =
        page(mvc.get().uri(PAGE)).head().select("link[rel=stylesheet], script[src]").stream()
            .map(asset -> asset.hasAttr("src") ? asset.attr("src") : asset.attr("href"))
            .toList();
    assertThat(assets)
        .hasSize(6)
        .allSatisfy(
            asset -> {
              assertThat(asset).matches(".*-[0-9a-f]{32}\\.(css|js)");
              assertThat(mvc.get().uri(asset)).hasStatusOk();
            });
  }

  private static Document page(MockMvcTester.MockMvcRequestBuilder request) {
    var result = request.exchange();
    assertThat(result).hasStatusOk();
    try {
      return Jsoup.parse(result.getResponse().getContentAsString());
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Today, for the pages: 1 June 2021, when the consolidation of 15 March 2021 applies. */
  @TestConfiguration(proxyBeanMethods = false)
  static class OnTheFirstOfJune2021 {
    @Bean
    @SuppressWarnings("TimeZoneUsage") // the app's Clock bean, fixed
    Clock firstOfJune2021() {
      return Clock.fixed(Instant.parse("2021-06-01T08:00:00Z"), ZoneOffset.UTC);
    }
  }
}
