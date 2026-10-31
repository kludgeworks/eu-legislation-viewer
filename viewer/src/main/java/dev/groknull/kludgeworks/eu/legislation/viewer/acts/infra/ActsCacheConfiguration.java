// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.infra;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Acts kept in Valkey for viewer.acts.cache-for (an hour), by CELEX and by ELI (CellarActs): an act
 * costs Cellar three or four SPARQL queries, and its timeline changes only when Cellar publishes a
 * consolidation. An act Cellar doesn't know isn't kept. As JSON, by Boot's Jackson with jMolecules'
 * module, so identifiers are their values; a timeline as its versions (a mixin, kept out of the
 * domain).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class ActsCacheConfiguration {
  /**
   * The caches by the shape of the JSON they keep: the number goes up when an act's JSON changes,
   * so an act kept before is never read (2: each version's languages).
   */
  static final String BY_CELEX = "acts-3";

  static final String BY_ELI = "acts-by-eli-3";

  /**
   * When not to keep an act: when there is none (an Optional's value, which Spring's caching
   * unwraps), as Cellar may publish it later.
   */
  static final String UNLESS_ABSENT = "#result == null";

  @Bean
  RedisCacheManagerBuilderCustomizer actsCaches(
      JsonMapper json, @Value("${viewer.acts.cache-for}") Duration cacheFor) {
    var acts =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(cacheFor)
            .disableCachingNullValues()
            .serializeValuesWith(SerializationPair.fromSerializer(serializer(json)));
    return caches ->
        caches.withCacheConfiguration(BY_CELEX, acts).withCacheConfiguration(BY_ELI, acts);
  }

  /** An act as JSON, by {@code json} and the mixin below. */
  static RedisSerializer<Act> serializer(JsonMapper json) {
    return new JacksonJsonRedisSerializer<>(
        json.rebuild().addMixIn(Timeline.class, TimelineAsVersions.class).build(), Act.class);
  }

  /**
   * A timeline as the array of its versions, read by Jackson itself: as a value, not a property,
   * which jMolecules would take for a single value and read without its versions' deserializer.
   */
  @SuppressWarnings("unused")
  abstract static class TimelineAsVersions {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    TimelineAsVersions(List<Version> versions) {}

    @JsonValue
    abstract List<Version> versions();
  }
}
