// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import dev.groknull.kludgeworks.build.flyway.PendingMigrations;
import dev.groknull.kludgeworks.build.flyway.RenamedMigrations;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The app's store and Valkey in containers (Testcontainers): Postgres, as the app's datasource, and
 * Valkey, for the broadcast events, through Boot's service connections, for the app's tests and for
 * running the app locally (TestViewerApplication). Its migrations as they are in the source, those
 * not released yet (NEXT_001__description.sql) included.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
  public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18.6");
  public static final DockerImageName VALKEY = DockerImageName.parse("valkey/valkey:9.1.2");

  /** Valkey's port. */
  public static final int VALKEY_PORT = 6379;

  /**
   * A new container, not started. It is reusable: when Testcontainers' reuse is on
   * (TESTCONTAINERS_REUSE_ENABLE=true, as bazel run //viewer:dev sets it), a running one is reused,
   * with what was ingested into it; elsewhere, each run starts its own.
   */
  public static PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(POSTGRES).withReuse(true);
  }

  /** A new Valkey container, not started; reusable as postgres() is. */
  public static GenericContainer<?> valkey() {
    return new GenericContainer<>(VALKEY).withExposedPorts(VALKEY_PORT).withReuse(true);
  }

  /** The migrations not released yet run too, after the released ones. */
  @Bean
  FlywayConfigurationCustomizer pendingMigrations() {
    return PendingMigrations::configure;
  }

  /**
   * A pending migration renamed (by the release, or the lint) is found again in a store it already
   * ran on: the one bazel run //viewer:dev keeps.
   */
  @Bean
  RenamedMigrations renamedMigrations() {
    return new RenamedMigrations();
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return postgres();
  }

  /** Valkey, as Redis: Boot knows its connection by the image's name, so it is named for it. */
  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> valkeyContainer() {
    return valkey();
  }
}
