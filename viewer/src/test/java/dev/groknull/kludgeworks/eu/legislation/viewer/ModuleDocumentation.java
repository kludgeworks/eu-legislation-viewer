// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import java.nio.file.Path;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.modulith.docs.Documenter.CanvasOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions;

/**
 * Writes the documentation of the modules. Run it with {@code mise run modules}. It uses the same
 * model that {@code ModularityTests} verifies, and Spring Modulith's {@link Documenter}. It writes:
 *
 * <ul>
 *   <li>a C4 component diagram of all the modules, in PlantUML,
 *   <li>a C4 component diagram of each module, in PlantUML,
 *   <li>the canvas of each module, in Asciidoc, with its API, events and configuration.
 * </ul>
 *
 * <p>The output goes into {@code viewer/spring-modulith-docs}, which is not committed.
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
