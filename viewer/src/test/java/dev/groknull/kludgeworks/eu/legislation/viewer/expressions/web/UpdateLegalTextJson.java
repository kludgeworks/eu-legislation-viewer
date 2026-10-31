// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rewrites the golden JSON (mise run expected:json): after a deliberate change to the records, the
 * module or the converter's output, then review the diff.
 */
public final class UpdateLegalTextJson {

  private UpdateLegalTextJson() {}

  public static void main(String[] args) throws IOException {
    var workspace = System.getenv("BUILD_WORKSPACE_DIRECTORY");
    if (workspace == null) {
      throw new IllegalStateException("run with bazel run (mise run expected:json)");
    }
    var root = Path.of(workspace, "viewer", "src", "test", "resources", "legal-text-json");
    for (var name : LegalTextJson.GOLDEN) {
      var file = LegalTextJson.golden(root, name);
      Files.createDirectories(file.getParent());
      Files.writeString(file, LegalTextJson.render(name), StandardCharsets.UTF_8);
      System.out.println("wrote " + root.relativize(file));
    }
  }
}
