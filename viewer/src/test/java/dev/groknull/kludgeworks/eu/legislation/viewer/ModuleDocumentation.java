// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import java.nio.file.Path;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.modulith.docs.Documenter.CanvasOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions;

/**
 * Writes the modules' documentation (mise run modules), from the same model ModularityTests
 * verifies: Spring Modulith's Documenter, a C4 component diagram of all the modules and one of each
 * (PlantUML), and each module's canvas (Asciidoc: its API, events and configuration), into
 * viewer/spring-modulith-docs, which isn't committed.
 */
public final class ModuleDocumentation {

  private ModuleDocumentation() {}

  public static void main(String[] args) {
    var workspace = System.getenv("BUILD_WORKSPACE_DIRECTORY");
    if (workspace == null) {
      throw new IllegalStateException("run with bazel run (mise run modules)");
    }
    var folder = Path.of(workspace, "viewer", "spring-modulith-docs");
    new Documenter(
            ApplicationModules.of(ViewerApplication.class, new ProductionClasses()),
            Documenter.Options.defaults().withOutputFolder(folder.toString()))
        .writeDocumentation(DiagramOptions.defaults(), CanvasOptions.defaults());
    System.out.println("wrote " + Path.of(workspace).relativize(folder));
  }
}
