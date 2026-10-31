// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.DefaultCurieProvider;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;

/**
 * The viewer's own link relations as {@code av:…} in HAL, each to be documented at {@code
 * /api/rels/{rel}} (pages to come). Only the relations named through ApiRelations are curied: by
 * default Spring HATEOAS curies every relation not on its own list of IANA's, and that list misses
 * some (RFC 5829's successor-version, which it spells successor-versions). Public for the modules'
 * web slice tests, which import it to render links as the application does.
 */
@Configuration(proxyBeanMethods = false)
public class CurieConfiguration {

  @Bean
  CurieProvider curies() {
    return new DefaultCurieProvider(ApiRelations.CURIE, UriTemplate.of("/api/rels/{rel}")) {
      @Override
      public HalLinkRelation getNamespacedRelFor(LinkRelation relation) {
        return HalLinkRelation.of(relation);
      }
    };
  }
}
