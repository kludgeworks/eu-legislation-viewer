// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.DefaultCurieProvider;

/**
 * The viewer's own link relations as {@code av:…} in HAL, each to be documented at {@code
 * /api/rels/{rel}} (pages to come); IANA's relations (self, alternate) stay as they are.
 */
@Configuration(proxyBeanMethods = false)
class CurieConfiguration {

  @Bean
  CurieProvider curies() {
    return new DefaultCurieProvider(ApiRelations.CURIE, UriTemplate.of("/api/rels/{rel}"));
  }
}
