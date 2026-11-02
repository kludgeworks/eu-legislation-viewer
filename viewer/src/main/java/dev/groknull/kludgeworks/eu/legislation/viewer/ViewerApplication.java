// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The amendment viewer: a Spring Boot application of Spring Modulith modules, one per top-level
 * package (acts, cellar, ingest), each a slice of the domain with its own rings (Sliced Onion).
 */
@SpringBootApplication
public class ViewerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ViewerApplication.class, args);
  }
}
