// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.SpringApplication;

/**
 * The viewer for running locally: Spring Boot's test main, where its containers (Testcontainers)
 * will come with the store. mise run app:dev, or bazel run //viewer:dev.
 */
public final class TestViewerApplication {

  private TestViewerApplication() {}

  public static void main(String[] args) {
    SpringApplication.from(ViewerApplication::main).run(args);
  }
}
