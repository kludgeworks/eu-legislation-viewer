// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiSnippets;
import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import java.time.Duration;
import java.util.regex.Pattern;
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

/**
 * Writes the catalogue's examples for the API's documentation, from the whole app on an empty
 * store: the act 2015/1998 as the recorded Cellar has it, and its texts from the converter's
 * fixtures, the consolidations of 22 March and 1 October 2026 in English. See {@link ApiSnippets}.
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
  private static final Pattern SETTLED = Pattern.compile("\"settledAt\" ?: ?\"");

  @Autowired MockMvcTester mvc;

  @Test
  void looksAnActUp() {
    // The first lookup answers 202 while the viewer reads the act from Cellar.
    await()
        .atMost(Duration.ofSeconds(60))
        .until(() ->
            get("/api/acts?celex=32015R1998").getResponse().getStatus()
                == HttpStatus.SEE_OTHER.value());

    assertThat(documented("act/lookup", "/api/acts?celex=32015R1998"))
        .hasStatus(HttpStatus.SEE_OTHER);
  }

  @Test
  void showsAnAct() {
    assertThat(documented("act/act", ACT)).hasStatusOk();
  }

  @Test
  void showsTheLawOnADay() {
    assertThat(documented("act/on", ACT + "/on/2026-04-01")).hasStatusOk();
  }

  @Test
  void showsTheHistoryOfAnAct() {
    assertThat(documented("act/history", ACT + "/history/texts/eng")).hasStatusOk();
  }

  @Test
  void showsTheHistoryOnADay() {
    assertThat(documented("act/history-on", ACT + "/history/texts/eng/on/2026-04-01"))
        .hasStatusOk();
  }

  @Test
  void showsAnOccurrenceOfAText() {
    var occurrence = ACT + "/versions/2026-10-01/texts/eng/occurrences/art_1";
    await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> get(occurrence).getResponse().getStatus() == HttpStatus.OK.value());

    assertThat(documented("act/occurrence", occurrence)).hasStatusOk();
  }

  @Test
  void answersADayBeforeTheFirstVersionWithAProblem() {
    assertThat(documented("act/before-first-version", ACT + "/on/1999-01-01"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void streamsTheEventsOfAPreparation() {
    // Of a settled preparation, so that the stream has one event and ends
    var preparation = ACT + "/preparations/eng";
    get(ACT + "/versions/2026-10-01/texts/eng");
    await()
        .atMost(Duration.ofSeconds(40))
        .until(() -> SETTLED.matcher(get(preparation).getResponse().getContentAsString()).find());

    var events =
        documented(
            "act/events",
            mvc.get()
                .uri(ACT + "/preparations/eng/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange());
    assertThat(events).hasStatusOk();
  }

  @Test
  void answersATextThatFailedWithAProblem() {
    var text = ACT + "/versions/oj/texts/eng";
    await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> get(text).getResponse().getContentAsString().contains("text-failed"));

    assertThat(documented("act/text-failed", text)).hasStatus(HttpStatus.NOT_FOUND);
  }

  private MvcTestResult documented(String name, String uri) {
    return documented(name, get(uri));
  }

  private static MvcTestResult documented(String name, MvcTestResult result) {
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
