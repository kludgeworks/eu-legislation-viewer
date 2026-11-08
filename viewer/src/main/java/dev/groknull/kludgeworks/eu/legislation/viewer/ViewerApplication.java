// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * The amendment viewer. It is a Spring Boot application of Spring Modulith modules, one for each
 * top-level package, for example catalogue, comparison, content and ingest. Each module is a slice
 * of the domain with its own rings, as in a Sliced Onion. All the modules share the language
 * module, so a module test always includes it. Code that several modules use, and that belongs to
 * none of them, is in libraries beside this package, which {@link LibrariesConfiguration}
 * registers.
 */
@SpringBootApplication
@Modulithic(sharedModules = "language")
public class ViewerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ViewerApplication.class, args);
  }
}
