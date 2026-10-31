// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.SpringApplication;

/**
 * The viewer for running locally, with its Valkey in a container (TestcontainersConfiguration):
 * Spring Boot's test main, so nothing but Docker is needed. mise run app:dev, or bazel run
 * //viewer:dev. With HAL Explorer, to browse the API from its root, at {@code
 * http://localhost:8080/hal} (DevConfiguration; its webjar is on this binary's classpath alone, not
 * the app's).
 */
public final class TestViewerApplication {

  private TestViewerApplication() {}

  public static void main(String[] args) {
    SpringApplication.from(ViewerApplication::main)
        .with(TestcontainersConfiguration.class, DevConfiguration.class)
        .run(args);
  }
}
