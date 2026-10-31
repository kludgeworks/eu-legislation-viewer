// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.DefaultCurieProvider;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.mediatype.hal.HalConfiguration.RenderSingleLinks;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.http.MediaType;

/**
 * How the API's links are written in HAL. The viewer's own relations as {@code av:…}, each to be
 * documented at {@code /api/rels/{rel}} (pages to come): only the relations named through
 * ApiRelations are curied, as by default Spring HATEOAS curies every relation not on its own list
 * of IANA's, and that list misses some (RFC 5829's successor-version, which it spells
 * successor-versions). And alternates always as an array, as a resource may have one or many (an
 * act its work in Cellar, a text each other language too), so a client reads one shape. Public for
 * the modules' web slice tests, which import it to render links as the application does.
 */
@Configuration(proxyBeanMethods = false)
public class HalLinksConfiguration {

  /**
   * In place of Spring Boot's, which it makes only when there's none: as Boot's, HAL for plain
   * application/json too (spring.hateoas.use-hal-as-default-json-media-type).
   */
  @Bean
  HalConfiguration halConfiguration() {
    return new HalConfiguration()
        .withMediaType(MediaType.APPLICATION_JSON)
        .withRenderSingleLinksFor(IanaLinkRelations.ALTERNATE, RenderSingleLinks.AS_ARRAY);
  }

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
