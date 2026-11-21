// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.LinkBuilder;
import org.springframework.hateoas.server.core.AbstractEntityLinks;
import org.springframework.hateoas.server.mvc.BasicLinkBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests the comparisons of an act in the API, with the catalogue, the content and the comparisons
 * mocked. The act is the act of {@link OneActCellar}, compared in English between its
 * consolidations of 15 March 2021 and 31 December 2021.
 */
@WebMvcTest(ApiComparisonController.class)
@Import({
  ApiConfiguration.class,
  ActComparisonLinks.class,
  ApiComparisonControllerTests.ActLinks.class
})
class ApiComparisonControllerTests {
  private static final String ACT = "http://localhost/api/acts/32015R1998";
  private static final String COMPARISON =
      "/api/acts/32015R1998/comparisons/2021-03-15..2021-12-31";
  private static final TextId EARLIER = new TextId(1);
  private static final TextId LATER = new TextId(2);
  private static final PublicText.Occurrence SHOWN =
      new PublicText.Occurrence(
          "anx/pnt_1.2",
          "point",
          "1.2.",
          null,
          null,
          List.of(),
          List.of(new PublicText.Text("Screening.", "<ins>Screening.</ins>", List.of())));

  @Autowired MockMvcTester mvc;
  @Autowired ActComparisonLinks actLinks;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;
  @MockitoBean Comparisons comparisons;
  @MockitoBean Content content;

  @BeforeEach
  void act() {
    when(acts.find(any(ActIdentifier.class))).thenReturn(Optional.empty());
    when(acts.find(OneActCellar.CELEX)).thenReturn(Optional.of(OneActCellar.ACT));
    when(content.outline(any())).thenReturn(Optional.empty());
    when(content.publicOccurrence(any(Element.class))).thenReturn(SHOWN);
    when(content.publicWording(any(Element.class)))
        .thenReturn(
            new PublicText.Edited(
                "",
                "Screening.",
                List.of(new PublicText.Change(PublicText.Change.Op.INSERT, "Screening."))));
    var difference = difference();
    when(comparisons.between(EARLIER, LATER))
        .thenReturn(
            Optional.of(new Comparison(EARLIER, LATER, List.of(difference), List.of(), null)));
    when(comparisons.difference(EARLIER, LATER, "anx/pnt_1.2")).thenReturn(Optional.of(difference));
  }

  @Test
  void looksTheComparisonOfTwoDatesUpAtTheFirstDaysOfTheirVersions() {
    assertThat(
            mvc.get()
                .uri("/api/acts/32015R1998/comparisons?from=2021-06-01&to=2022-01-10&language=eng"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, ACT + "/comparisons/2021-03-15..2021-12-31/texts/eng");
  }

  @Test
  void refusesALookupThatLacksADateOrHasThemReversed() {
    assertThat(mvc.get().uri("/api/acts/32015R1998/comparisons?from=2021-06-01&language=eng"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            mvc.get()
                .uri("/api/acts/32015R1998/comparisons?from=2022-01-10&to=2021-06-01&language=eng"))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void listsTheDifferencesWithTheirTagsAndLinks() throws UnsupportedEncodingException {
    textsAre(new Text.Ready(EARLIER), new Text.Ready(LATER));

    var comparison = json(mvc.get().uri(COMPARISON + "/texts/eng").exchange());

    assertThat(comparison.at("/from").asString()).isEqualTo("2021-03-15");
    assertThat(comparison.at("/to").asString()).isEqualTo("2021-12-31");
    assertThat(comparison.at("/language").asString()).isEqualTo("eng");
    var difference = comparison.at("/_embedded/item/0");
    assertThat(difference.at("/eId").asString()).isEqualTo("anx/pnt_1.2");
    assertThat(difference.at("/kind").asString()).isEqualTo("changed");
    assertThat(difference.at("/inserted").asInt()).isEqualTo(3);
    assertThat(difference.at("/tags/0/act").asString()).isEqualTo("32019R0103");
    assertThat(difference.at("/_links/self/href").asString())
        .isEqualTo(ACT + "/comparisons/2021-03-15..2021-12-31/texts/eng/differences/anx/pnt_1.2");
    assertThat(comparison.at("/_links/up/href").asString()).isEqualTo(ACT);
    assertThat(comparison.at("/_links/av:difference/href").asString())
        .isEqualTo(ACT + "/comparisons/2021-03-15..2021-12-31/texts/eng/differences/{+eId}");
  }

  @Test
  void answersAComparisonWhoseTextsAreNotReadyWithAccepted() {
    textsAre(new Text.Ready(EARLIER), new Text.Converting(1));

    assertThat(mvc.get().uri(COMPARISON + "/texts/eng"))
        .hasStatus(HttpStatus.ACCEPTED)
        .headers()
        .hasValue(HttpHeaders.RETRY_AFTER, "5");
  }

  @Test
  void doesNotFindAComparisonAtDatesThatStartNoVersion() {
    textsAre(new Text.Ready(EARLIER), new Text.Ready(LATER));

    assertThat(mvc.get().uri("/api/acts/32015R1998/comparisons/2021-03-16..2021-12-31/texts/eng"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/acts/32015R1998/comparisons/2021-12-31..2021-03-15/texts/eng"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void showsADifferenceWholeWithItsEdits() throws UnsupportedEncodingException {
    textsAre(new Text.Ready(EARLIER), new Text.Ready(LATER));

    var difference =
        json(mvc.get().uri(COMPARISON + "/texts/eng/differences/anx/pnt_1.2").exchange());

    assertThat(difference.at("/eId").asString()).isEqualTo("anx/pnt_1.2");
    assertThat(difference.at("/after").asString()).isEqualTo("Screening.");
    assertThat(difference.at("/changes/0/op").asString()).isEqualTo("insert");
    assertThat(difference.at("/shown/content/0/html").asString())
        .isEqualTo("<ins>Screening.</ins>");
    assertThat(difference.at("/_links/up/href").asString())
        .isEqualTo(ACT + "/comparisons/2021-03-15..2021-12-31/texts/eng");
    assertThat(mvc.get().uri(COMPARISON + "/texts/eng/differences/art_99"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void linksAnActToTheLookupOfItsComparisons() {
    var act = actLinks.process(EntityModel.of(OneActCellar.ACT));

    assertThat(act.getRequiredLink(ActComparisonLinks.COMPARISONS).getHref())
        .endsWith("/api/acts/32015R1998/comparisons{?from,to,language,amendingAct}");
    assertThat(act.getRequiredLink(ActComparisonLinks.COMPARISONS).isTemplated()).isTrue();
  }

  /** Has the preparation in English hold these texts for the two consolidations of 2021. */
  private void textsAre(Text earlier, Text later) {
    var versions = OneActCellar.ACT.timeline().versions();
    var held = new LinkedHashMap<Version, Text>();
    held.put(versions.get(1), earlier);
    held.put(versions.get(2), later);
    when(texts.prepare(OneActCellar.ACT, Language.ENG))
        .thenReturn(
            new Preparation(
                OneActCellar.CELEX,
                Language.ENG,
                Instant.parse("2021-06-01T08:00:00Z"),
                null,
                held,
                List.of(),
                0));
  }

  private static Difference difference() {
    var occurrence = new Element("occurrence", Map.of("eId", "anx/pnt_1.2"), List.of(), "");
    return new Difference(
        "anx/pnt_1.2",
        Kind.CHANGED,
        new Sibling("anx/pnt_1.2", "level", "1.2"),
        occurrence,
        3,
        1,
        "anx/pnt_1",
        null,
        "anx/pnt_1.2",
        null,
        List.of(),
        List.of(new Tag("32019R0103", "replaced", null)),
        List.of(),
        null);
  }

  private static JsonNode json(MvcTestResult result) throws UnsupportedEncodingException {
    return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
  }

  /** Links to an act, as the catalogue gives them. */
  @TestConfiguration
  static class ActLinks {
    @Bean
    EntityLinks actLinks() {
      return new AbstractEntityLinks() {
        @Override
        public boolean supports(Class<?> type) {
          return Act.class.equals(type);
        }

        @Override
        public LinkBuilder linkFor(Class<?> type) {
          return BasicLinkBuilder.linkToCurrentMapping().slash("api").slash("acts");
        }

        @Override
        public LinkBuilder linkFor(Class<?> type, Object... parameters) {
          return linkFor(type);
        }

        @Override
        public LinkBuilder linkForItemResource(Class<?> type, Object id) {
          return linkFor(type).slash(ActIdentifier.class.cast(id).celex());
        }

        @Override
        public Link linkToCollectionResource(Class<?> type) {
          return linkFor(type).withSelfRel();
        }

        @Override
        public Link linkToItemResource(Class<?> type, Object id) {
          return linkForItemResource(type, id).withSelfRel();
        }
      };
    }
  }
}
