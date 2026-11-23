// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Release;
import gg.jte.generated.precompiled.StaticTemplates;
import gg.jte.generated.precompiled.Templates;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * What the tests of the comparison's fragments share: the act of {@link OneActCellar} with its
 * texts built or not, and the templates, the release and the clock that the test slice leaves out.
 */
final class FragmentFixtures {
  private FragmentFixtures() {}

  /**
   * The act of {@link OneActCellar} with its texts built, in {@code language}: the version from
   * 2026-03-22 as text 1, from 2026-10-01 as text 2, the others as text 9.
   */
  static Preparation built(Language language) {
    var texts = new LinkedHashMap<Version, Text>();
    OneActCellar.ACT
        .timeline()
        .versions()
        .forEach(
            version ->
                texts.put(
                    version,
                    new Text.Ready(
                        new TextId(
                            switch (version.date().toString()) {
                              case "2026-03-22" -> 1;
                              case "2026-10-01" -> 2;
                              default -> 9;
                            }))));
    return new Preparation(
        OneActCellar.CELEX,
        language,
        Instant.parse("2026-11-06T08:00:00Z"),
        null,
        texts,
        List.of(),
        0);
  }

  /** The act of {@link OneActCellar} while none of its texts is built, in {@code language}. */
  static Preparation queued(Language language) {
    var texts = new LinkedHashMap<Version, Text>();
    OneActCellar.ACT
        .timeline()
        .versions()
        .forEach(version -> texts.put(version, new Text.Waiting(0)));
    return new Preparation(
        OneActCellar.CELEX,
        language,
        Instant.parse("2026-11-06T08:00:00Z"),
        null,
        texts,
        List.of(),
        0);
  }

  /** The templates of the fragments, as the app has them. The test slice leaves them out. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Pages {
    @Bean
    Templates templates() {
      return new StaticTemplates();
    }

    @Bean
    Release release() {
      return new Release("2026.11.5");
    }

    @Bean
    @SuppressWarnings("TimeZoneUsage") // a fixed stand-in for the app's Clock bean
    Clock sixthOfNovember2026() {
      return Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), ZoneOffset.UTC);
    }
  }
}
