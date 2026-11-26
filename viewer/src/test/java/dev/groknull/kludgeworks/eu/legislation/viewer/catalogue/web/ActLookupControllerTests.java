// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.BrusselsToday;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.CompareService;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.HistoryService;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ReadService;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import java.io.UnsupportedEncodingException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jsoup.Jsoup;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Tests an act's page and the API while the catalogue does not have the act yet, with {@code
 * WebMvcTest} and the lookups mocked. The page waits at the address asked for, and its stream says
 * how the lookup ended. The act is 2015/1998, of {@link OneActCellar}.
 */
@WebMvcTest({VersionController.class, ActLookupController.class, ApiActController.class})
@Import({
  ControllerTesting.class,
  HistoryService.class,
  ReadService.class,
  CompareService.class,
  ApiConfiguration.class,
  ActLookupControllerTests.OnTheFirstOfJune2021.class
})
class ActLookupControllerTests {
  private static final String PAGE = "/eli/reg_impl/2015/1998/2026-03-22/eng";

  /** The address of the stream, as the page has it. */
  private static final String EVENTS =
      "/eli/reg_impl/2015/1998/lookup/events?page=%2Feli%2Freg_impl%2F2015%2F1998%2F2026-03-22%2Feng";

  /** The address of the stream, for MockMvc, which encodes the query itself. */
  private static final String STREAM = "/eli/reg_impl/2015/1998/lookup/events?page=" + PAGE;

  @Autowired MockMvcTester mvc;
  @MockitoBean Lookups lookups;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;
  @MockitoBean Content content;

  @Test
  void waitsAtTheAddressAskedForWhileItLooksTheActUp() {
    when(lookups.lookUp(OneActCellar.ELI)).thenReturn(new CompletableFuture<>());

    var result = mvc.get().uri(PAGE).exchange();

    assertThat(result).hasStatusOk();
    var page = Jsoup.parse(body(result));
    var lookup = Objects.requireNonNull(page.getElementById("act-lookup"));
    assertThat(lookup.select("p").eachText())
        .containsExactly(
            "Looking up reg_impl/2015/1998 at the Publications Office…",
            "The Publications Office is slow to answer.");
    assertThat(lookup.attr("hx-get")).isEqualTo(PAGE);
    // htmx follows a redirect of the address, and shows the address that it got.
    assertThat(lookup.attr("hx-replace-url")).isEqualTo("true");
    assertThat(lookup.attr("hx-trigger")).isEqualTo("done");
    var stream = Objects.requireNonNull(lookup.selectFirst("[hx-sse:connect]"));
    assertThat(stream.attr("hx-sse:connect")).isEqualTo(EVENTS);
    assertThat(stream.attr("hx-sse:close")).isEqualTo("done");
    assertThat(page.title()).isEqualTo("Looking up reg_impl/2015/1998 · Amendment viewer");
  }

  @Test
  void saysThatNoActHasTheAddress() {
    when(lookups.lookUp(OneActCellar.ELI))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    var result = mvc.get().uri(PAGE).exchange();

    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    var missing = Objects.requireNonNull(Jsoup.parse(body(result)).selectFirst("#page .failure"));
    assertThat(missing.select("h2").text()).isEqualTo("No act has this address");
    assertThat(missing.select("p").text())
        .isEqualTo("The Publications Office has no act with the ELI reg_impl/2015/1998.");
    assertThat(missing.select("a").attr("href")).isEqualTo("/");
  }

  @Test
  void streamsDoneWhenTheLookupFoundTheAct() {
    when(lookups.lookUp(OneActCellar.ELI))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(OneActCellar.ACT)));

    assertThat(body(mvc.get().uri(STREAM).exchange())).contains("event:done");
  }

  @Test
  void streamsThatNoActHasTheAddress() {
    when(lookups.lookUp(OneActCellar.ELI))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    var message = body(mvc.get().uri(STREAM).exchange());

    assertThat(message).doesNotContain("event:done").contains("hx-target=\"#act-lookup\"");
    assertThat(Jsoup.parse(message).select("h2").text()).isEqualTo("No act has this address");
  }

  @Test
  void streamsThatThePublicationsOfficeDidNotAnswer() {
    when(lookups.lookUp(OneActCellar.ELI))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Read timed out")));

    var message = Jsoup.parse(body(mvc.get().uri(STREAM).exchange()));

    assertThat(message.select("h2").text()).isEqualTo("The lookup did not finish");
    assertThat(message.select("a").eachAttr("href"))
        .containsExactly(PAGE, "https://eur-lex.europa.eu/eli/reg_impl/2015/1998/oj");
  }

  @Test
  void refusesTheStreamForAPageOfAnotherAct() {
    assertThat(mvc.get().uri("/eli/reg_impl/2015/1998/lookup/events?page=/eli/reg/2008/767/eng"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void answersTheApiWithAcceptedWhileItLooksTheActUp() {
    when(lookups.lookUp(OneActCellar.CELEX)).thenReturn(new CompletableFuture<Optional<Act>>());

    assertThat(mvc.get().uri("/api/acts/32015R1998"))
        .hasStatus(HttpStatus.ACCEPTED)
        .headers()
        .hasValue(HttpHeaders.RETRY_AFTER, "2")
        .hasValue(HttpHeaders.LOCATION, "/api/acts/32015R1998");
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @Import(BrusselsToday.class)
  static class OnTheFirstOfJune2021 {
    @Bean
    @SuppressWarnings("TimeZoneUsage") // a fixed stand-in for the app's Clock bean
    Clock firstOfJune2021() {
      return Clock.fixed(Instant.parse("2021-06-01T08:00:00Z"), ZoneOffset.UTC);
    }
  }
}
