// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.legaltext.LegalRenderer;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.StepCounts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.StaticTemplates;
import gg.jte.generated.precompiled.Templates;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests the comparison's parts of the compare page alone, with {@code @WebMvcTest} and the modules
 * mocked. The tests check what the parts refuse, and that they link back to the page that they are
 * part of. The act is the act of {@link OneActCellar}, with none of its texts built yet. {@code
 * ChangesPagesTests} tests the parts as the whole app works them out.
 */
@WebMvcTest(ChangesController.class)
@Import(ChangesControllerTests.Pages.class)
class ChangesControllerTests {
  private static final String ACT = "/eli/reg_impl/2015/1998/";
  private static final String PAGE = ACT + "eng/compare?from=2026-03-22&to=2026-10-01";
  private static final String CHANGES = ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01";
  private static final String ACTS = ACT + "eng/compare/acts?from=2026-03-22&to=2026-10-01";

  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;
  @MockitoBean Comparisons comparisons;
  @MockitoBean StepCounts stepCounts;
  @MockitoBean Content content;
  @MockitoBean LegalRenderer html;

  @BeforeEach
  void act() {
    when(acts.find(OneActCellar.ELI)).thenReturn(Optional.of(OneActCellar.ACT));
    when(texts.preparation(any(), any())).thenAnswer(asked -> queued(asked.getArgument(1)));
  }

  @Test
  void showsNoViewThereIsnt() {
    assertThat(mvc.get().uri(CHANGES + "&view=sideways").accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void answersOnlyTheCanonicalAddressOfAComparison() {
    // Not the first day of a version
    assertThat(
            mvc.get()
                .uri(ACT + "eng/compare/changes?from=2026-04-01&to=2026-10-01")
                .accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
    // From after To
    assertThat(
            mvc.get()
                .uri(ACT + "eng/compare/acts?from=2026-10-01&to=2026-03-22")
                .accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
    // An act that is not between the two versions
    assertThat(mvc.get().uri(ACTS + "&act=32019R0103").accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void linksTheChipsBackToThePageTheyArePartOf() throws UnsupportedEncodingException {
    var result = mvc.get().uri(ACTS + "&view=whole").exchange();
    assertThat(result).hasStatusOk();
    var chips = Jsoup.parse(result.getResponse().getContentAsString());

    var chip = Objects.requireNonNull(chips.selectFirst("#acts a.chip"));
    assertThat(chip.select(".label").text()).isEqualTo("2026/449");
    assertThat(chip.attr("href")).isEqualTo(PAGE + "&act=32026R0449&view=whole");
    // Neither text is built, so nothing is counted.
    assertThat(chip.select(".count")).isEmpty();
  }

  /** The act of {@link OneActCellar} while none of its texts is built, in {@code language}. */
  private static Preparation queued(Language language) {
    var texts = new LinkedHashMap<Version, Text>();
    OneActCellar.ACT
        .timeline()
        .versions()
        .forEach(version -> texts.put(version, new Text.Queued()));
    return new Preparation(
        OneActCellar.CELEX,
        language,
        Instant.parse("2026-11-06T08:00:00Z"),
        null,
        texts,
        List.of(),
        0);
  }

  /** The templates of the parts, as the app has them. The test slice leaves them out. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Pages {
    @Bean
    Templates templates() {
      return new StaticTemplates();
    }
  }
}
