// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import net.sf.saxon.s9api.Processor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Configures the converter on one Saxon processor. The processor is thread-safe and costly to make.
 * It also enables the resilience annotations of Spring, for the limit on conversions that run at
 * once in {@link FormexConversion}.
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
