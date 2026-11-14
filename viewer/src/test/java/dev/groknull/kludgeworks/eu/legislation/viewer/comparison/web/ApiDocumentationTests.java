// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiSnippets;
import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the comparison's examples for the API's documentation, from the whole app on an empty
 * store: 2015/1998 as the recorded Cellar has it, compared in English between its consolidations of
 * 22 March and 1 October 2026, the converter's fixtures. See {@link ApiSnippets}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  OneActCellar.class,
  FormexFixturesConfiguration.class,
  ApiSnippets.Configuration.class
})
@ExtendWith(ApiSnippets.class)
class ApiDocumentationTests {
  private static final String ACT = "/api/acts/32015R1998";
  private static final String COMPARISON = ACT + "/comparisons/2026-03-22..2026-10-01/texts/eng";

  @Autowired MockMvcTester mvc;

  @Test
  void looksAComparisonUp() {
    assertThat(
            documented(
                "comparisons/lookup",
                ACT + "/comparisons?from=2026-04-01&to=2026-10-15&language=eng"))
        .hasStatus(HttpStatus.SEE_OTHER);
  }

  @Test
  void showsAComparisonAndOneOfItsDifferences() throws UnsupportedEncodingException {
    await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> get(COMPARISON).getResponse().getStatus() == HttpStatus.OK.value());

    var comparison = documented("comparisons/comparison", COMPARISON);
    var first =
        JsonMapper.shared()
            .readTree(comparison.getResponse().getContentAsString())
            .at("/_embedded/item/0/_links/self/href")
            .asString();

    assertThat(documented("comparisons/difference", first)).hasStatusOk();
  }

  private MvcTestResult documented(String name, String uri) {
    var result = get(uri);
    try {
      ApiSnippets.document(name).handle(result.getMvcResult());
    } catch (Exception e) {
      throw new IllegalStateException("Could not document " + name, e);
    }
    return result;
  }

  private MvcTestResult get(String uri) {
    return mvc.get().uri(uri).accept(MediaType.parseMediaType("application/hal+json")).exchange();
  }
}
