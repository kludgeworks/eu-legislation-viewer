// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The app's containers (Testcontainers): Valkey, configured as in production: a pure cache, with no
 * persistence and bounded memory, evicting the least recently used. As a Spring configuration it is
 * the app's Valkey (spring.data.redis.*, through Boot's service connection for Redis, whose
 * protocol Valkey speaks), for the app's tests and for running the app locally
 * (TestViewerApplication).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
  public static final DockerImageName VALKEY = DockerImageName.parse("valkey/valkey:9.1.2");

  /**
   * A new container, not started. It is reusable: when Testcontainers' reuse is on
   * (TESTCONTAINERS_REUSE_ENABLE=true, as mise run app:dev sets it), a running one is reused, with
   * the texts already converted into it; elsewhere, each run starts its own.
   */
  public static GenericContainer<?> valkey() {
    return new GenericContainer<>(VALKEY)
        .withExposedPorts(6379)
        .withCommand(
            "valkey-server",
            "--maxmemory",
            "256mb",
            "--maxmemory-policy",
            "allkeys-lru",
            "--save",
            "",
            "--appendonly",
            "no")
        .withReuse(true);
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> valkeyContainer() {
    return valkey();
  }
}
