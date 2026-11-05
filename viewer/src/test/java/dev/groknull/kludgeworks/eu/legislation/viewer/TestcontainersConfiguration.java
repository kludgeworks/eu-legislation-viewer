// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The app's store in a container (Testcontainers): Postgres, as the app's datasource through Boot's
 * service connection, for the app's tests and for running the app locally (TestViewerApplication).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
  public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18.6");

  /**
   * A new container, not started. It is reusable: when Testcontainers' reuse is on
   * (TESTCONTAINERS_REUSE_ENABLE=true, as bazel run //viewer:dev sets it), a running one is reused,
   * with what was ingested into it; elsewhere, each run starts its own.
   */
  public static PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(POSTGRES).withReuse(true);
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return postgres();
  }
}
