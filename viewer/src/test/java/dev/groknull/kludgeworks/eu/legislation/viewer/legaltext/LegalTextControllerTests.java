// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Release;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests how the fragments of texts are cached by browsers, with {@code @WebMvcTest} and the content
 * mocked: a browser asks again each time, the entity tag is the release and the text, a browser
 * that has it gets 304 with nothing read, and a text that is no longer stored is 404.
 */
@WebMvcTest(LegalTextController.class)
@Import(LegalTextControllerTests.Released.class)
class LegalTextControllerTests {
  private static final TextId TEXT = new TextId(42);
  private static final String UNITS = "/content/42/units?first=art_1&last=art_3";
  private static final String ETAG = "W/\"2026.11.5-42\"";

  @Autowired MockMvcTester mvc;
  @MockitoBean Content content;
  @MockitoBean RenderedTexts texts;

  @BeforeEach
  void stored() {
    when(content.contains(TEXT)).thenReturn(true);
    when(texts.whole(TEXT)).thenReturn(Optional.of("<article>Whole</article>"));
    when(texts.units(TEXT, "art_1", "art_3")).thenReturn(Optional.of("<p>Article 1</p>"));
  }

  @Test
  void keepsUnitsForAYearByTheReleaseAndTheText() {
    var result = mvc.get().uri(UNITS).exchange();

    assertThat(result).hasStatusOk().hasBodyTextEqualTo("<p>Article 1</p>");
    assertThat(result)
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-cache")
        .hasValue(HttpHeaders.ETAG, ETAG);
  }

  @Test
  void answersABrowserThatHasTheUnitsWithoutRenderingThem() {
    var result = mvc.get().uri(UNITS).header(HttpHeaders.IF_NONE_MATCH, ETAG).exchange();

    assertThat(result).hasStatus(HttpStatus.NOT_MODIFIED);
    assertThat(result).headers().hasValue(HttpHeaders.ETAG, ETAG);
    verify(texts, never()).units(any(), any(), any());
  }

  @Test
  void rendersTheUnitsAgainForAnotherRelease() {
    var result =
        mvc.get().uri(UNITS).header(HttpHeaders.IF_NONE_MATCH, "W/\"2026.11.4-42\"").exchange();

    assertThat(result).hasStatusOk().hasBodyTextEqualTo("<p>Article 1</p>");
  }

  @Test
  void keepsTheWholeTextByTheReleaseAndTheText() {
    var result = mvc.get().uri("/content/42").exchange();

    assertThat(result).hasStatusOk().hasBodyTextEqualTo("<article>Whole</article>");
    assertThat(result)
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-cache")
        .hasValue(HttpHeaders.ETAG, ETAG);
  }

  @Test
  void answersABrowserThatHasTheWholeTextWithoutRenderingIt() {
    var result = mvc.get().uri("/content/42").header(HttpHeaders.IF_NONE_MATCH, ETAG).exchange();

    assertThat(result).hasStatus(HttpStatus.NOT_MODIFIED);
    verify(texts, never()).whole(any());
  }

  @Test
  void answersATextNoLongerStoredWithNotFoundEvenToABrowserThatHasIt() {
    when(content.contains(TEXT)).thenReturn(false);

    assertThat(mvc.get().uri(UNITS).header(HttpHeaders.IF_NONE_MATCH, ETAG))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/content/42")).hasStatus(HttpStatus.NOT_FOUND);
    verify(texts, never()).whole(any());
    verify(texts, never()).units(any(), any(), any());
  }

  /** The release, as a release build stamps it. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Released {
    @Bean
    Release release() {
      return new Release("2026.11.5");
    }
  }
}
