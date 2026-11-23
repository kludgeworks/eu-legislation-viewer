// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.FragmentFixtures.queued;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributions;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests the comparison's views alone, with {@code @WebMvcTest} and the modules mocked: what they
 * refuse. The act is the act of {@link OneActCellar}, with none of its texts built yet. {@code
 * DifferencesPagesTests} tests the views as the whole app works them out.
 */
@WebMvcTest(ComparisonViewsController.class)
@Import(FragmentFixtures.Pages.class)
class ComparisonViewsControllerTests {
  private static final String ACT = "/eli/reg_impl/2015/1998/";
  private static final String DIFFERENCES =
      ACT + "eng/compare/differences?from=2026-03-22&to=2026-10-01";

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
  }

  @Test
  void showsNoViewThereIsnt() {
    assertThat(mvc.get().uri(DIFFERENCES + "&view=sideways").accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void answersOnlyTheCanonicalAddressOfAComparison() {
    // Not the first day of a version
    assertThat(
            mvc.get()
                .uri(ACT + "eng/compare/differences?from=2026-04-01&to=2026-10-01")
                .accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
    // From after To
    assertThat(
            mvc.get()
                .uri(ACT + "eng/compare/differences?from=2026-10-01&to=2026-03-22")
                .accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
    // An act that is not between the two versions
    assertThat(mvc.get().uri(DIFFERENCES + "&act=32019R0103").accept(MediaType.TEXT_HTML))
        .hasStatus(HttpStatus.NOT_FOUND);
  }
}
