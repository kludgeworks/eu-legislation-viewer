// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.BrusselsToday;
import java.io.UnsupportedEncodingException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the tests of the resources of an act in the API share: the address of the act of {@link
 * dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar}, today as 1 June 2021,
 * when its consolidation of 15 March 2021 applies, and the reading of an answer.
 */
final class ApiActTesting {
  static final String ACT = "http://localhost/api/acts/32015R1998";

  private ApiActTesting() {}

  static List<String> texts(JsonNode array, String pointer) {
    return StreamSupport.stream(array.spliterator(), false)
        .map(each -> (pointer.isEmpty() ? each : each.at(pointer)).asString())
        .toList();
  }

  static JsonNode json(MvcTestResult result) throws UnsupportedEncodingException {
    return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
  }

  /** Today is 1 June 2021. */
  @TestConfiguration(proxyBeanMethods = false)
  @Import(BrusselsToday.class)
  static class OnTheFirstOfJune2021 {
    @Bean
    @SuppressWarnings("TimeZoneUsage") // a fixed stand-in for the app's Clock bean
    Clock firstOfJune2021() {
      return Clock.fixed(Instant.parse("2021-06-01T08:00:00Z"), ZoneOffset.UTC);
    }
  }

  /** Assemblers of the resources of an act in the API. */
  @TestConfiguration(proxyBeanMethods = false)
  @Import({
    OnTheFirstOfJune2021.class,
    ActModelAssembler.class,
    VersionModelAssembler.class,
    AmendmentsModelAssembler.class,
    HistoryModelAssembler.class,
    LawModelAssembler.class,
    PreparationModelAssembler.class,
    TextModelAssembler.class
  })
  static class Assemblers {}
}
