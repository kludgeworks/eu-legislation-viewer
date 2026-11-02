// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * The amendment viewer: a Spring Boot application of Spring Modulith modules, one per top-level
 * package (catalogue, cellar, content, ingest), each a slice of the domain with its own rings
 * (Sliced Onion); language is shared by all of them, so a module test always includes it.
 */
@SpringBootApplication
@Modulithic(sharedModules = "language")
public class ViewerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ViewerApplication.class, args);
  }
}
