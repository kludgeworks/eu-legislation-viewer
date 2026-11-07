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
 * Runs the app's store and Valkey in containers with Testcontainers. Postgres is the app's
 * datasource. Valkey carries the broadcast events. Boot's service connections connect the app to
 * both. The app's tests use this configuration, and so does {@code TestViewerApplication} to run
 * the app locally.
 *
 * <p>The store gets the migrations as they are in the source. This includes the migrations not
 * released yet, such as {@code NEXT_001__description.sql}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
  public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18.6");
  public static final DockerImageName VALKEY = DockerImageName.parse("valkey/valkey:9.1.2");

  /** Valkey's port. */
  public static final int VALKEY_PORT = 6379;

  /**
   * A new Postgres container, not started. It is reusable. When Testcontainers' reuse is on, a
   * running container is reused, with what was ingested into it. {@code bazel run //viewer:dev}
   * turns reuse on with {@code TESTCONTAINERS_REUSE_ENABLE=true}. Elsewhere, each run starts its
   * own container.
   */
  public static PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(POSTGRES).withReuse(true);
  }

  /** A new Valkey container, not started. It is reusable in the same way as {@link #postgres()}. */
  public static GenericContainer<?> valkey() {
    return new GenericContainer<>(VALKEY).withExposedPorts(VALKEY_PORT).withReuse(true);
  }

  /** The migrations not released yet run too, after the released ones. */
  @Bean
  FlywayConfigurationCustomizer pendingMigrations() {
    return PendingMigrations::configure;
  }

  /**
   * Finds a pending migration again after the release or the lint renames it, in a store it already
   * ran on. That store is the one that {@code bazel run //viewer:dev} keeps.
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

  /**
   * Valkey, named as Redis. Boot finds the connection by the name of the image, so the connection
   * has the name {@code redis}.
   */
  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> valkeyContainer() {
    return valkey();
  }
}
