// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import gg.jte.generated.precompiled.StaticTemplates;
import gg.jte.generated.precompiled.Templates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The pages' templates, by jte-models' typed facade on the templates jte_library precompiled; the
 * legal text's, by name (LegalRenderer, on the TemplateEngine jte's starter configures).
 */
@Configuration(proxyBeanMethods = false)
class PagesConfiguration {

  @Bean
  Templates templates() {
    return new StaticTemplates();
  }
}
