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
 * What a controller's test (@WebMvcTest) needs beside the controller and its mocked modules: the
 * pages' templates, as the app has them (PagesConfiguration, which the slice leaves out).
 */
@TestConfiguration(proxyBeanMethods = false)
class ControllerTesting {
  @Bean
  Templates templates() {
    return new StaticTemplates();
  }

  /** OneActCellar's act being prepared in {@code language}: none of its texts built yet. */
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
