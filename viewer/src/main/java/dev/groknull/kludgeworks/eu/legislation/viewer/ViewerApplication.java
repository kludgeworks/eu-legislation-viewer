// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The amendment viewer: a Spring Boot application of Spring Modulith modules, one per top-level
 * package (acts, cellar, expressions, and later amendments, comparison), each a slice of the domain
 * with its own adapters (Sliced Onion). The API's vocabulary, its root and how its links are
 * written, is the acts module's web adapter's, which the other modules' build on.
 */
@SpringBootApplication
public class ViewerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ViewerApplication.class, args);
  }
}
