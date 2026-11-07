// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.StaticTemplates;
import gg.jte.generated.precompiled.Templates;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Gives a controller test with {@code @WebMvcTest} what it needs beside the controller and its
 * mocked modules. That is the templates of the pages, as the app has them. In the app, {@code
 * PagesConfiguration} gives them, but the test slice leaves it out.
 */
@TestConfiguration(proxyBeanMethods = false)
class ControllerTesting {
  @Bean
  Templates templates() {
    return new StaticTemplates();
  }

  /**
   * The act of {@link OneActCellar} while it is prepared in {@code language}. None of its texts is
   * built yet.
   */
  static Preparation preparing(Language language) {
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
}
