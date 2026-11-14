// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiConfiguration;
import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Tests the root of the API: its links, and how a controller of the API answers. */
@WebMvcTest(ApiRootController.class)
@Import(ApiConfiguration.class)
class ApiRootControllerTests {
  @Autowired MockMvcTester mvc;

  @Test
  void linksToTheActsAndToTheDocumentation() throws UnsupportedEncodingException {
    var result = mvc.get().uri("/api").accept(MediaTypes.HAL_JSON).exchange();
    assertThat(result).hasStatusOk().hasContentType(MediaTypes.HAL_JSON);
    var links = json(result).at("/_links");

    assertThat(links.at("/self/href").asString()).isEqualTo("http://localhost/api");
    assertThat(links.at("/av:act/href").asString()).isEqualTo("http://localhost/api/acts/{celex}");
    assertThat(links.at("/av:act/templated").asBoolean()).isTrue();
    assertThat(links.at("/av:act-by-celex/href").asString())
        .isEqualTo("http://localhost/api/acts{?celex}");
    assertThat(links.at("/av:act-by-eli/href").asString())
        .isEqualTo("http://localhost/api/acts{?eli}");
    assertThat(links.at("/service-doc/href").asString()).isEqualTo("http://localhost/api/docs");
  }

  @Test
  void documentsItsOwnRelationsAtTheAddressOfTheCurie() throws UnsupportedEncodingException {
    var curies = json(mvc.get().uri("/api").exchange()).at("/_links/curies");

    assertThat(curies).hasSize(1);
    assertThat(curies.at("/0/name").asString()).isEqualTo("av");
    assertThat(curies.at("/0/href").asString()).isEqualTo("http://localhost/api/docs#{rel}");
  }

  @Test
  void labelsTheDocumentAsHalForARequestThatAcceptsJsonOrAnything() {
    assertThat(mvc.get().uri("/api").accept(MediaType.APPLICATION_JSON).exchange())
        .hasStatusOk()
        .hasContentType(MediaTypes.HAL_JSON);
    assertThat(mvc.get().uri("/api").accept(MediaType.ALL).exchange())
        .hasStatusOk()
        .hasContentType(MediaTypes.HAL_JSON);
  }

  @Test
  void refusesARequestThatAcceptsOnlyATypeThatIsNotJson() {
    assertThat(mvc.get().uri("/api").accept(MediaType.APPLICATION_XML).exchange())
        .hasStatus(HttpStatus.NOT_ACCEPTABLE);
  }

  @Test
  void letsAPageOfAnotherSiteReadIt() {
    assertThat(mvc.get().uri("/api").header(HttpHeaders.ORIGIN, "https://example.org").exchange())
        .hasStatusOk()
        .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
  }

  private static JsonNode json(MvcTestResult result) throws UnsupportedEncodingException {
    return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
  }
}
