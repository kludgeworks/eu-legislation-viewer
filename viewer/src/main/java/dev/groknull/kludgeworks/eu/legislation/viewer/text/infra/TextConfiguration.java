// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import dev.groknull.kludgeworks.formex.converter.FormexConverter;
import net.sf.saxon.s9api.Processor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The converter and the reader, on one Saxon processor (thread-safe, and costly to make). */
@Configuration(proxyBeanMethods = false)
class TextConfiguration {

  @Bean
  Processor saxon() {
    return new Processor(false);
  }

  @Bean
  FormexConverter formexConverter(Processor saxon) {
    return new FormexConverter(saxon);
  }

  @Bean
  LegalTextReader legalTextReader(Processor saxon) {
    return new LegalTextReader(saxon);
  }
}
