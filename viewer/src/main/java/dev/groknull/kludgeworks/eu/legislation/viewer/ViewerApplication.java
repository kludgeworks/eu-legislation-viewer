// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * The amendment viewer: a Spring Boot application of Spring Modulith modules, one per top-level
 * package (acts, cellar, text, and later amendments, comparison). Event listeners marked @Async run
 * on Boot's task executor, on virtual threads.
 */
@SpringBootApplication
@EnableAsync
public class ViewerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ViewerApplication.class, args);
  }
}
