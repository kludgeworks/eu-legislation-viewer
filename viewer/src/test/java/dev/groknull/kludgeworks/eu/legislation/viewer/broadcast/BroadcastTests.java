// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.modulith.events.Externalized;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests broadcasting through Valkey in a container. The test publishes an externalized event, a
 * {@link Pinged} to the stream {@code broadcast-tests}, in a transaction. The listener hears it as
 * a {@link Broadcast} after the commit. The test also puts an entry of a type that is not
 * externalized in that stream by hand. The listener does not read that entry back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, BroadcastTests.Listening.class})
class BroadcastTests {
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transaction;
  @Autowired StringRedisTemplate redis;
  @Autowired Heard heard;

  @Test
  void sendsAnExternalizedEventToEveryInstanceOnceCommitted() {
    var ping = new Pinged(UUID.randomUUID().toString());
    transaction.executeWithoutResult(
        status -> {
          events.publishEvent(ping);
          assertThat(heard.pings()).doesNotContain(ping);
        });

    await().atMost(Duration.ofSeconds(30)).until(() -> heard.pings().contains(ping));
    assertThat(heard.pings()).containsOnlyOnce(ping);
  }

  @Test
  void readsBackOnlyTheAppsExternalizedTypes() {
    var later = new Pinged(UUID.randomUUID().toString());
    redis
        .opsForStream()
        .add(
            StreamRecords.string(Map.of("type", "java.lang.ProcessBuilder", "payload", "{}"))
                .withStreamKey("broadcast-tests"));
    transaction.executeWithoutResult(status -> events.publishEvent(later));

    // The stream is read in order. When the listener hears the later ping, it has skipped the
    // entry before it.
    await().atMost(Duration.ofSeconds(30)).until(() -> heard.pings().contains(later));
    assertThat(heard.all()).allMatch(Pinged.class::isInstance);
  }

  /** A test's event, externalized. */
  @Externalized("broadcast-tests")
  record Pinged(String id) {}

  /** Listens for broadcasts in the test's context. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Listening {
    @Bean
    Heard heard() {
      return new Heard();
    }
  }

  /** The broadcasts this instance heard. */
  static class Heard {
    private final List<Object> heard = new CopyOnWriteArrayList<>();

    @EventListener
    void on(Broadcast<?> broadcast) {
      heard.add(broadcast.event());
    }

    List<Object> all() {
      return List.copyOf(heard);
    }

    List<Pinged> pings() {
      return heard.stream().filter(Pinged.class::isInstance).map(Pinged.class::cast).toList();
    }
  }
}
