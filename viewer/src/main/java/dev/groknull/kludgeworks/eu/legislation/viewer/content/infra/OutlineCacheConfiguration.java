// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.infra;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.application.ContentCaches;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configures the cache of outlines, in the process, with Caffeine. It keeps the outlines of the
 * texts read last, by the identity of the text, up to {@code viewer.content.outline-cache.size}. A
 * text identity is never used again, so no entry is ever wrong and none is invalidated: an outline
 * of a text that was replaced is not asked for again, and is evicted in time.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutlineCacheConfiguration.OutlineCacheProperties.class)
class OutlineCacheConfiguration {

  @Bean
  CaffeineCacheManager outlineCacheManager(OutlineCacheProperties properties) {
    var manager = new CaffeineCacheManager();
    manager.setCaffeine(Caffeine.newBuilder().maximumSize(properties.size()));
    manager.setAllowNullValues(false);
    manager.setCacheNames(List.of(ContentCaches.OUTLINES));
    return manager;
  }

  /**
   * The settings of the cache of outlines, the {@code viewer.content.outline-cache.*} properties.
   *
   * @param size how many outlines are kept. An outline is about 100 KB of JSON, at most 500 KB.
   */
  @ConfigurationProperties("viewer.content.outline-cache")
  @Validated
  record OutlineCacheProperties(@Positive long size) {}
}
