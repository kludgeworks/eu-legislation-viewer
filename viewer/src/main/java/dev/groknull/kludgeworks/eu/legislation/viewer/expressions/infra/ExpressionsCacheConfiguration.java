// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.time.Duration;
import java.util.Set;
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
 * The languages a document has Formex in, kept in Valkey for viewer.expressions.languages-for (an
 * hour, as acts: Cellar adds a language when it publishes one) by its CELEX (CellarFormexSource): a
 * SPARQL query each time a version is shown otherwise. As JSON, an array of the languages' codes.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class ExpressionsCacheConfiguration {
  static final String LANGUAGES = "formex-languages";

  @Bean
  RedisCacheManagerBuilderCustomizer expressionsCaches(
      JsonMapper json, @Value("${viewer.expressions.languages-for}") Duration languagesFor) {
    return caches ->
        caches.withCacheConfiguration(
            LANGUAGES,
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(languagesFor)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(serializer(json))));
  }

  /** A set of languages as JSON, by {@code json}. */
  static RedisSerializer<Set<Language>> serializer(JsonMapper json) {
    return new JacksonJsonRedisSerializer<>(
        json, json.getTypeFactory().constructCollectionType(Set.class, Language.class));
  }
}
