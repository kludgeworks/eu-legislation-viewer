// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.FragmentFixtures.built;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.FragmentFixtures.queued;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributions;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.DifferVersion;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests one difference of a comparison, and the differences at a place, alone, with
 * {@code @WebMvcTest} and the modules mocked: what they refuse, and their entity tag. The act is
 * the act of {@link OneActCellar}. {@code DifferencesPagesTests} tests the fragments as the whole
 * app works them out.
 */
@WebMvcTest(DifferenceController.class)
@Import(FragmentFixtures.Pages.class)
class DifferenceControllerTests {
  private static final String ACT = "/eli/reg_impl/2015/1998/";
  private static final String DIFFERENCE =
      ACT + "eng/compare/difference?from=2026-03-22&to=2026-10-01&eId=art_1";
  private static final String PLACE =
      ACT + "eng/compare/place?from=2026-03-22&to=2026-10-01&eId=art_1&eId=art_2";

  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;
  @MockitoBean Comparisons comparisons;
  @MockitoBean Attributions attributions;
  @MockitoBean Content content;
  @MockitoBean LegalRenderer html;

  @BeforeEach
  void act() {
    when(acts.find(OneActCellar.ELI)).thenReturn(Optional.of(OneActCellar.ACT));
    when(texts.preparation(any(), any())).thenAnswer(asked -> queued(asked.getArgument(1)));
    when(html.forComparison(any(), any())).thenReturn(html);
    when(html.deleted()).thenReturn(html);
  }

  @Test
  void answersABrowserThatHasADifferenceWithoutComparing() {
    when(texts.preparation(any(), any())).thenAnswer(asked -> built(asked.getArgument(1)));
    var etag = "W/\"2026.11.5-1-2-" + DifferVersion.value() + "\"";

    var result = mvc.get().uri(DIFFERENCE).header(HttpHeaders.IF_NONE_MATCH, etag).exchange();

    assertThat(result).hasStatus(HttpStatus.NOT_MODIFIED);
    assertThat(result)
        .headers()
        .hasValue(HttpHeaders.ETAG, etag)
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-cache");
    verify(comparisons, never()).difference(any(), any(), any());
  }

  @Test
  void comparesAgainForABrowserThatHasADifferenceOfOtherTexts() {
    when(texts.preparation(any(), any())).thenAnswer(asked -> built(asked.getArgument(1)));

    // The text at To was replaced: the browser has the difference with the text before it.
    var stale = "W/\"2026.11.5-1-7-" + DifferVersion.value() + "\"";
    mvc.get().uri(DIFFERENCE).header(HttpHeaders.IF_NONE_MATCH, stale).exchange();

    verify(comparisons).difference(new TextId(1), new TextId(2), "art_1");
  }

  @Test
  void saysThatAPlaceIsTheSameInBothTexts() throws UnsupportedEncodingException {
    when(texts.preparation(any(), any())).thenAnswer(asked -> built(asked.getArgument(1)));
    when(comparisons.between(new TextId(1), new TextId(2)))
        .thenReturn(
            Optional.of(new Comparison(new TextId(1), new TextId(2), List.of(), List.of(), null)));

    var result = mvc.get().uri(PLACE).exchange();

    assertThat(result).hasStatusOk();
    assertThat(Jsoup.parse(result.getResponse().getContentAsString()).select("p.hint").text())
        .isEqualTo("The text before this act and the text with it have the same wording here.");
  }

  @Test
  void findsNoPlaceWhileTheTextsAreNotBuilt() {
    assertThat(mvc.get().uri(PLACE).accept(MediaType.TEXT_HTML)).hasStatus(HttpStatus.NOT_FOUND);
  }
}
