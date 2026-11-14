// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Release;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.validation.annotation.Validated;

/**
 * Configures the cache of rendered HTML in Valkey, on the connection of the app.
 *
 * <ul>
 *   <li>A key is {@code html:{release}:{text}} for a whole text, and {@code
 *       html:{release}:{text}:{first}:{last}} for a run of its units. A text identity is never used
 *       again, and a release changes the templates, so no entry is ever wrong and none is
 *       invalidated.
 *   <li>An entry lives for {@code viewer.legaltext.html-cache.ttl}. Valkey evicts the least
 *       recently used entries first when its memory is full, if its {@code maxmemory-policy} is
 *       {@code allkeys-lru}. That is server configuration, not set here.
 *   <li>The values are the HTML as UTF-8 strings.
 *   <li>If Valkey fails, the HTML is rendered as if it were not cached, and the failure is logged
 *       (the app's {@code CachingConfiguration}).
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HtmlCacheConfiguration.HtmlCacheProperties.class)
class HtmlCacheConfiguration {
  /** The name of the cache. It is the first part of each key. */
  static final String CACHE = "html";

  @Bean
  RedisCacheManager htmlCacheManager(
      RedisConnectionFactory connections, Release release, HtmlCacheProperties properties) {
    return RedisCacheManager.builder(connections)
        .cacheDefaults(
            RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(name -> prefix(name, release))
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .disableCachingNullValues()
                .entryTtl(properties.ttl()))
        .initialCacheNames(Set.of(CACHE))
        .disableCreateOnMissingCache()
        .build();
  }

  /**
   * Returns where the keys of the cache {@code name} start, for example {@code html:2026.11.5:}.
   */
  static String prefix(String name, Release release) {
    return name + ":" + release.value() + ":";
  }

  /**
   * The settings of the cache of rendered HTML, the {@code viewer.legaltext.html-cache.*}
   * properties.
   *
   * @param ttl how long an entry lives after it is written
   */
  @ConfigurationProperties("viewer.legaltext.html-cache")
  @Validated
  record HtmlCacheProperties(@NotNull Duration ttl) {}
}
