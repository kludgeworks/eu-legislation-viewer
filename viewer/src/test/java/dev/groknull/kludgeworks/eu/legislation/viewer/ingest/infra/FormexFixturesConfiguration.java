// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Formex from the converter's fixtures (FormexFixtures) in place of Cellar's, for the app's tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FormexFixturesConfiguration {

  @Bean
  @Primary
  FormexSource formexFixtures() {
    return new FormexFixtures();
  }
}
