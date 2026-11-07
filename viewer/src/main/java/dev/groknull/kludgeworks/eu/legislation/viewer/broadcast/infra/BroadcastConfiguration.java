// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.infra;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.Externalized;
import org.springframework.modulith.events.support.EventExternalizerModuleListener;
import org.springframework.util.ClassUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configures broadcasting over Valkey.
 *
 * <ul>
 *   <li>Modulith externalizes each {@code @Externalized} event through the event registry, once its
 *       transaction commits. {@link StreamTransport} adds the event to its stream.
 *   <li>Every instance reads those streams with a {@link StreamReader}, on a virtual thread. It
 *       reads from the entry that was last added when the instance started.
 *   <li>The {@code @Externalized} types of the app set the streams, and the types that an entry may
 *       be read back as. They are found in the packages of the app at startup.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class BroadcastConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(BroadcastConfiguration.class);

  /** How long a read waits for an entry before reading again. */
  private static final Duration POLL = Duration.ofSeconds(10);

  /**
   * How long to wait before reading again after a read failed. Mostly, a read fails because Valkey
   * is unreachable.
   */
  private static final Duration AFTER_FAILURE = Duration.ofSeconds(5);

  @Bean
  EventExternalizerModuleListener streamExternalizer(
      EventExternalizationConfiguration configuration, StringRedisTemplate redis, JsonMapper json) {
    return new EventExternalizerModuleListener(configuration, new StreamTransport(redis, json));
  }

  @Bean
  StreamMessageListenerContainer<String, MapRecord<String, String, String>> broadcastStreams(
      RedisConnectionFactory connections,
      StringRedisTemplate redis,
      JsonMapper json,
      ApplicationEventPublisher events,
      BeanFactory beans) {
    var types = externalized(beans);
    var executor = new SimpleAsyncTaskExecutor("broadcast-");
    executor.setVirtualThreads(true);
    var container =
        StreamMessageListenerContainer.create(
            connections,
            StreamMessageListenerContainerOptions.builder()
                .pollTimeout(POLL)
                .executor(executor)
                .build());
    var reader = new StreamReader(types, json, events);
    var streams = new TreeMap<String, String>();
    types.values().forEach(type -> streams.put(stream(type), type.getSimpleName()));
    for (var stream : streams.keySet()) {
      container.register(
          StreamReadRequest.builder(StreamOffset.create(stream, after(redis, stream)))
              .cancelOnError(e -> false)
              .errorHandler(BroadcastConfiguration::failed)
              .build(),
          reader);
    }
    LOG.info("Reading streams {} for the broadcast events {}", streams.keySet(), types.keySet());
    return container;
  }

  /**
   * Returns where to read {@code stream} from. This is after the entry last added to it, or from
   * its start if it has no entries. Thus no entry added after this instance started is missed, not
   * even before its first read. Reading from the latest entry, {@code $}, would miss these. If
   * Valkey cannot tell the last entry, it reads from the latest.
   */
  private static ReadOffset after(StringRedisTemplate redis, String stream) {
    try {
      var last =
          redis.opsForStream().reverseRange(stream, Range.unbounded(), Limit.limit().count(1));
      return last == null || last.isEmpty()
          ? ReadOffset.from("0-0")
          : ReadOffset.from(last.getFirst().getId());
    } catch (RuntimeException e) {
      LOG.warn(
          "Reading stream {} from its latest entry: its last isn't known ({})",
          stream,
          e.toString());
      return ReadOffset.latest();
    }
  }

  /** Returns the {@code @Externalized} types of the app, in its packages, by name. */
  private static Map<String, Class<?>> externalized(BeanFactory beans) {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Externalized.class));
    var types = new TreeMap<String, Class<?>>();
    for (var pkg : AutoConfigurationPackages.get(beans)) {
      for (var candidate : scanner.findCandidateComponents(pkg)) {
        var name = Objects.requireNonNull(candidate.getBeanClassName());
        types.put(
            name, ClassUtils.resolveClassName(name, BroadcastConfiguration.class.getClassLoader()));
      }
    }
    return types;
  }

  /**
   * Returns the stream that the {@code @Externalized} of {@code type} names. This is its target,
   * before any routing key, which follows {@code ::}.
   */
  private static String stream(Class<?> type) {
    var target =
        Objects.requireNonNull(AnnotatedElementUtils.findMergedAnnotation(type, Externalized.class))
            .target();
    var stream = target.contains("::") ? target.substring(0, target.indexOf("::")) : target;
    if (stream.isBlank()) {
      throw new IllegalStateException(type.getName() + " is @Externalized without a stream");
    }
    return stream;
  }

  /** Logs a failed read, and waits a little, so that the next read is not at once. */
  private static void failed(Throwable failure) {
    LOG.warn(
        "Reading the broadcast streams failed, again in {}: {}",
        AFTER_FAILURE,
        failure.getMessage());
    try {
      Thread.sleep(AFTER_FAILURE);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
