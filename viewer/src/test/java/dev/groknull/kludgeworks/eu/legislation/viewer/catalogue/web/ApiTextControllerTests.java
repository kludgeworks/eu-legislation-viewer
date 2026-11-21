// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests the text of a version in the API, with its outline and units, with the catalogue and the
 * content mocked. The act is the act of {@link OneActCellar}, and the text asked for is its
 * consolidation of 15 March 2021 in English.
 */
@WebMvcTest(ApiTextController.class)
@Import({ApiConfiguration.class, CataloguedTesting.class})
class ApiTextControllerTests {
  private static final String ACT = "http://localhost/api/acts/32015R1998";
  private static final String VERSION = ACT + "/versions/2021-03-15";
  private static final String TEXT = "/api/acts/32015R1998/versions/2021-03-15/texts/eng";
  private static final TextId STORED = new TextId(7);
  private static final PublicText.Unit UNIT =
      new PublicText.Unit(
          "anx/pnt_1.2",
          "point",
          "1.2.",
          null,
          null,
          List.of(),
          List.of(new PublicText.Text("Screening.", "<i>Screening.</i>", List.of())));

  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;
  @MockitoBean Content content;

  @BeforeEach
  void act() {
    when(acts.find(any(ActIdentifier.class))).thenReturn(Optional.empty());
    when(acts.find(OneActCellar.CELEX)).thenReturn(Optional.of(OneActCellar.ACT));
    when(content.publicText(STORED))
        .thenReturn(
            Optional.of(
                new PublicText(null, List.of(), List.of(), List.of(), List.of(UNIT), List.of())));
    when(content.publicUnit(any(), any())).thenReturn(Optional.empty());
    when(content.publicUnit(STORED, "anx/pnt_1.2"))
        .thenReturn(Optional.of(new PublicText.Extract(UNIT, List.of())));
    when(content.outline(STORED))
        .thenReturn(
            Optional.of(
                new Outline(
                    List.of(
                        new Outline.Entry(
                            "anx",
                            "annex",
                            "ANNEX",
                            "",
                            List.of(
                                new Outline.Entry(
                                    "anx/pnt_1", "level", "1.", "AIRPORT SECURITY", List.of())))),
                    List.of("anx", "anx/pnt_1"),
                    Map.of())));
  }

  @Test
  void servesAReadyTextInThePublicModelWithItsLinks() throws UnsupportedEncodingException {
    textIs(new Text.Ready(STORED));

    var result = mvc.get().uri(TEXT).accept(MediaTypes.HAL_JSON).exchange();

    assertThat(result)
        .hasStatusOk()
        .hasContentType(MediaTypes.HAL_JSON)
        .headers()
        .hasValue(HttpHeaders.CONTENT_LANGUAGE, "en")
        .hasValue(HttpHeaders.ETAG, "W/\"7\"");
    var text = json(result);
    assertThat(text.at("/version").asString()).isEqualTo("2021-03-15");
    assertThat(text.at("/celex").asString()).isEqualTo("02015R1998-20210315");
    assertThat(text.at("/language").asString()).isEqualTo("eng");
    assertThat(text.at("/text/annexes/0/type").asString()).isEqualTo("unit");
    assertThat(text.at("/text/annexes/0/content/0/html").asString()).isEqualTo("<i>Screening.</i>");
    assertThat(text.at("/text/body").isMissingNode()).isTrue();
    assertThat(text.at("/_links/self/href").asString()).isEqualTo(VERSION + "/texts/eng");
    assertThat(text.at("/_links/up/href").asString()).isEqualTo(VERSION);
    assertThat(text.at("/_links/alternate/href").asString()).isEqualTo(VERSION + "/texts/fra");
    assertThat(text.at("/_links/alternate/hreflang").asString()).isEqualTo("fr");
    assertThat(text.at("/_links/av:outline/href").asString())
        .isEqualTo(VERSION + "/texts/eng/outline");
    assertThat(text.at("/_links/av:unit/href").asString())
        .isEqualTo(VERSION + "/texts/eng/units/{+eId}");
    assertThat(text.at("/_links/av:unit/templated").asBoolean()).isTrue();
    assertThat(text.at("/_links/av:preparation/href").asString())
        .isEqualTo(ACT + "/preparations/eng");
    verify(texts).prepare(OneActCellar.ACT, Language.ENG);
  }

  @Test
  void answersAClientThatHasTheTextWithNotModified() {
    textIs(new Text.Ready(STORED));

    assertThat(mvc.get().uri(TEXT).header(HttpHeaders.IF_NONE_MATCH, "W/\"7\""))
        .hasStatus(HttpStatus.NOT_MODIFIED);
  }

  @Test
  void answersATextThatIsNotReadyWithItsPreparation() throws UnsupportedEncodingException {
    textIs(new Text.Converting(1));

    var result = mvc.get().uri(TEXT).exchange();

    assertThat(result)
        .hasStatus(HttpStatus.ACCEPTED)
        .headers()
        .hasValue(HttpHeaders.RETRY_AFTER, "5")
        .doesNotContainHeader(HttpHeaders.ETAG);
    var preparation = json(result);
    assertThat(preparation.at("/_links/self/href").asString()).isEqualTo(ACT + "/preparations/eng");
    assertThat(preparation.at("/_links/av:events/href").asString())
        .isEqualTo(ACT + "/preparations/eng/events");
    assertThat(preparation.at("/_embedded/item/0/state").asString()).isEqualTo("converting");
    assertThat(preparation.at("/_embedded/item/0/_links/av:text").isMissingNode()).isTrue();
  }

  @Test
  void answersATextThatFailedWithAProblem() throws UnsupportedEncodingException {
    textIs(new Text.Failed(Failure.UNAVAILABLE, null));

    var result = mvc.get().uri(TEXT).exchange();

    assertThat(result)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    var problem = json(result);
    assertThat(problem.at("/type").asString()).isEqualTo("http://localhost/api/docs#text-failed");
    assertThat(problem.at("/failure").asString()).isEqualTo("unavailable");
    assertThat(problem.at("/retryable").asBoolean()).isTrue();
    assertThat(problem.at("/preparation").asString()).isEqualTo(ACT + "/preparations/eng");
  }

  @Test
  void doesNotFindATextInALanguageTheVersionDoesNotHave() {
    textIs(new Text.Ready(STORED));

    for (var path :
        new String[] {
          "/api/acts/32015R1998/versions/2021-03-15/texts/deu",
          "/api/acts/32015R1998/versions/2021-03-15/texts/xyz",
          "/api/acts/32015R1998/versions/2021-03-16/texts/eng",
          "/api/acts/32099R9999/versions/oj/texts/eng"
        }) {
      assertThat(mvc.get().uri(path)).as(path).hasStatus(HttpStatus.NOT_FOUND);
    }
  }

  @Test
  void servesTheOutlineOfAText() throws UnsupportedEncodingException {
    textIs(new Text.Ready(STORED));

    var outline = json(mvc.get().uri(TEXT + "/outline").exchange());

    assertThat(outline.at("/entries/0/eId").asString()).isEqualTo("anx");
    assertThat(outline.at("/entries/0/entries/0/heading").asString()).isEqualTo("AIRPORT SECURITY");
    assertThat(outline.at("/_links/self/href").asString())
        .isEqualTo(VERSION + "/texts/eng/outline");
    assertThat(outline.at("/_links/up/href").asString()).isEqualTo(VERSION + "/texts/eng");
    assertThat(outline.at("/_links/av:unit/href").asString())
        .isEqualTo(VERSION + "/texts/eng/units/{+eId}");
  }

  @Test
  void servesAUnitByItsEIdWithItsSlash() throws UnsupportedEncodingException {
    textIs(new Text.Ready(STORED));

    var unit = json(mvc.get().uri(TEXT + "/units/anx/pnt_1.2").exchange());

    assertThat(unit.at("/unit/eId").asString()).isEqualTo("anx/pnt_1.2");
    assertThat(unit.at("/unit/content/0/text").asString()).isEqualTo("Screening.");
    assertThat(unit.at("/_links/self/href").asString())
        .isEqualTo(VERSION + "/texts/eng/units/anx/pnt_1.2");
    assertThat(unit.at("/_links/up/href").asString()).isEqualTo(VERSION + "/texts/eng");
    assertThat(mvc.get().uri(TEXT + "/units/art_99")).hasStatus(HttpStatus.NOT_FOUND);
  }

  /** Has the preparation in English hold {@code text} for the consolidation of 15 March 2021. */
  private void textIs(Text text) {
    var states = new LinkedHashMap<Version, Text>();
    states.put(OneActCellar.ACT.timeline().versions().get(1), text);
    var preparation =
        new Preparation(
            OneActCellar.CELEX,
            Language.ENG,
            Instant.parse("2021-06-01T08:00:00Z"),
            null,
            states,
            List.of(),
            0);
    when(texts.prepare(OneActCellar.ACT, Language.ENG)).thenReturn(preparation);
  }

  private static JsonNode json(MvcTestResult result) throws UnsupportedEncodingException {
    return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
  }
}
