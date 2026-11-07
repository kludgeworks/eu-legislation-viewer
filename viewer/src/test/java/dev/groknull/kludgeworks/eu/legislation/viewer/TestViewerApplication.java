// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.SpringApplication;

/**
 * Runs the viewer locally, with its store and Grafana in containers. The containers come from
 * {@link TestcontainersConfiguration} and {@link ObservabilityContainers}. This is Spring Boot's
 * test main, so only Docker is necessary. Run it with {@code mise run app:dev} or {@code bazel run
 * //viewer:dev}.
 */
public final class TestViewerApplication {

  private TestViewerApplication() {}

  public static void main(String[] args) {
    SpringApplication.from(ViewerApplication::main)
        .with(TestcontainersConfiguration.class, ObservabilityContainers.class)
        .run(args);
  }
}
