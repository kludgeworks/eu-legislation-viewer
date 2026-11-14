// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests the preparation of an act in a language in the API, with the catalogue mocked. The act is
 * the act of {@link OneActCellar}, prepared in English.
 */
@WebMvcTest(ApiPreparationController.class)
@Import(ApiConfiguration.class)
class ApiPreparationControllerTests {
  private static final String ACT = "http://localhost/api/acts/32015R1998";
  private static final String PREPARATION = "/api/acts/32015R1998/preparations/eng";

  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;

  @BeforeEach
  void act() {
    when(acts.find(any(ActIdentifier.class))).thenReturn(Optional.empty());
    when(acts.find(OneActCellar.CELEX)).thenReturn(Optional.of(OneActCellar.ACT));
  }

  @Test
  void showsTheStateOfEachTextWithALinkToTheTextsThatAreReady()
      throws UnsupportedEncodingException {
    preparationIs(
        null,
        new Text.Ready(new TextId(7)),
        new Text.Converting(2),
        new Text.Waiting(0),
        new Text.Failed(Failure.NO_FORMEX, null),
        new Text.Waiting(1));

    var preparation = json(mvc.get().uri(PREPARATION).exchange());

    assertThat(preparation.at("/language").asString()).isEqualTo("eng");
    assertThat(preparation.at("/ready").asInt()).isEqualTo(1);
    assertThat(preparation.at("/converting").asInt()).isEqualTo(1);
    assertThat(preparation.at("/settledAt").isNull()).isTrue();
    var items = preparation.at("/_embedded/item");
    assertThat(items.at("/0/version").asString()).isEqualTo("oj");
    assertThat(items.at("/0/state").asString()).isEqualTo("ready");
    assertThat(items.at("/0/_links/av:text/href").asString())
        .isEqualTo(ACT + "/versions/oj/texts/eng");
    assertThat(items.at("/1/state").asString()).isEqualTo("converting");
    assertThat(items.at("/1/attempt").asInt()).isEqualTo(2);
    assertThat(items.at("/1/_links").isMissingNode()).isTrue();
    assertThat(items.at("/3/failure").asString()).isEqualTo("no-formex");
    assertThat(items.at("/3/retryable").asBoolean()).isFalse();
    assertThat(preparation.at("/_links/up/href").asString()).isEqualTo(ACT);
    assertThat(preparation.at("/_links/av:events/href").asString())
        .isEqualTo(ACT + "/preparations/eng/events");
    assertThat(preparation.at("/_links/av:retry").isMissingNode()).isTrue();
  }

  @Test
  void triesAgainTheTextsThatFailedInAWayThatTryingAgainMayHelp()
      throws UnsupportedEncodingException {
    preparationIs(Instant.parse("2021-06-01T08:05:00Z"), new Text.Failed(Failure.ERROR, null));

    var preparation = json(mvc.get().uri(PREPARATION).exchange());
    assertThat(preparation.at("/_links/av:retry/href").asString())
        .isEqualTo(ACT + "/preparations/eng");

    assertThat(mvc.post().uri(PREPARATION)).hasStatus(HttpStatus.ACCEPTED);
    verify(texts).retry(OneActCellar.ACT, Language.ENG);
  }

  @Test
  void refusesToTryAgainWhenTryingAgainCannotHelp() {
    preparationIs(
        Instant.parse("2021-06-01T08:05:00Z"), new Text.Failed(Failure.UNSUPPORTED, null));

    assertThat(mvc.post().uri(PREPARATION)).hasStatus(HttpStatus.CONFLICT);
    verify(texts, never()).retry(any(), any());
  }

  @Test
  void endsTheStreamOfASettledPreparationWithItsState() throws UnsupportedEncodingException {
    preparationIs(Instant.parse("2021-06-01T08:05:00Z"), new Text.Ready(new TextId(7)));

    var result = mvc.get().uri(PREPARATION + "/events").exchange();

    assertThat(result).hasStatusOk();
    var body = result.getResponse().getContentAsString();
    assertThat(body)
        .startsWith("event:settled\ndata:{")
        .contains("\"ready\":1")
        .doesNotContain("_links");
  }

  @Test
  void doesNotFindAPreparationInALanguageThatNoVersionHas() {
    preparationIs(null, new Text.Waiting(0));

    assertThat(mvc.get().uri("/api/acts/32015R1998/preparations/deu"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/acts/32099R9999/preparations/eng"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  /** Has the preparation in English hold {@code states}, for the versions in date order. */
  private void preparationIs(@Nullable Instant settledAt, Text... states) {
    var versions = OneActCellar.ACT.timeline().versions();
    var held = new LinkedHashMap<Version, Text>();
    for (var i = 0; i < states.length; i++) {
      held.put(versions.get(i), states[i]);
    }
    var preparation =
        new Preparation(
            OneActCellar.CELEX,
            Language.ENG,
            Instant.parse("2021-06-01T08:00:00Z"),
            settledAt,
            held,
            List.of(),
            0);
    when(texts.preparation(OneActCellar.ACT, Language.ENG)).thenReturn(preparation);
  }

  private static JsonNode json(MvcTestResult result) throws UnsupportedEncodingException {
    return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
  }
}
