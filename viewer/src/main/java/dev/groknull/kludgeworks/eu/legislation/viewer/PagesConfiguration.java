// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import gg.jte.generated.precompiled.StaticTemplates;
import gg.jte.generated.precompiled.Templates;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The pages' templates, by jte-models' typed facade on the templates jte_library precompiled; the
 * legal text's, by name (LegalRenderer, on the TemplateEngine jte's starter configures). And their
 * today.
 */
@Configuration(proxyBeanMethods = false)
class PagesConfiguration {

  @Bean
  Templates templates() {
    return new StaticTemplates();
  }

  /**
   * What time it is, as the pages tell which version applies today: the one Clock, which the rest
   * take the time from, as TimeZoneUsage asks of them; in UTC, each saying its own zone.
   */
  @Bean
  @SuppressWarnings("TimeZoneUsage") // the bean TimeZoneUsage has the others inject
  Clock clock() {
    return Clock.systemUTC();
  }
}
