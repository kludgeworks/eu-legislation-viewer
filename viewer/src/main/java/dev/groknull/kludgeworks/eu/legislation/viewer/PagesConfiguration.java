// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import gg.jte.generated.precompiled.StaticTemplates;
import gg.jte.generated.precompiled.Templates;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the templates of the pages, and their clock.
 *
 * <p>The pages use the typed facade of jte-models on the templates that {@code jte_library}
 * precompiled. The legal text uses its templates by name, through {@code LegalRenderer}, on the
 * {@code TemplateEngine} that the jte starter configures.
 */
@Configuration(proxyBeanMethods = false)
class PagesConfiguration {

  @Bean
  Templates templates() {
    return new StaticTemplates();
  }

  /**
   * Returns the one {@link Clock}, which the pages use to tell which version applies today. The
   * rest of the code takes the time from it, as the {@code TimeZoneUsage} check asks. The clock is
   * in UTC, and each user of it gives its own zone.
   */
  @Bean
  @SuppressWarnings("TimeZoneUsage") // TimeZoneUsage has the other classes inject this bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
