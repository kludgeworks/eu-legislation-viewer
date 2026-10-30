// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import net.sf.saxon.s9api.Processor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * The converter and the reader, on one Saxon processor (thread-safe, and costly to make); the
 * store's access to Valkey: string keys, byte values (gzipped XML).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StoreProperties.class)
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

  @Bean
  RedisTemplate<String, byte[]> legalTextStore(RedisConnectionFactory connections) {
    var template = new RedisTemplate<String, byte[]>();
    template.setConnectionFactory(connections);
    template.setKeySerializer(RedisSerializer.string());
    template.setValueSerializer(RedisSerializer.byteArray());
    return template;
  }
}
