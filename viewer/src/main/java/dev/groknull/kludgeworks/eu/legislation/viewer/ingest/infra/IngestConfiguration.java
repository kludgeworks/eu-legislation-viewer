// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import net.sf.saxon.s9api.Processor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * The converter on one Saxon processor (thread-safe, and costly to make); and Spring's resilience
 * annotations, for the limit on conversions running at once (FormexConversion).
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
class IngestConfiguration {

  @Bean
  Processor saxon() {
    return new Processor(false);
  }

  @Bean
  FormexConverter formexConverter(Processor saxon) {
    return new FormexConverter(saxon);
  }
}
