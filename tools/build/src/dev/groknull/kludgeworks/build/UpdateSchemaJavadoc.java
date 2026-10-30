// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the schema's documentation into the records' Javadoc ({@link SchemaJavadoc}), changing
 * nothing else in the files; then format them (google-java-format), as hk does. Run with bazel run
 * on the macro's update target.
 */
public final class UpdateSchemaJavadoc {

  private UpdateSchemaJavadoc() {}

  public static void main(String[] args) throws IOException {
    var workspace = System.getenv("BUILD_WORKSPACE_DIRECTORY");
    if (workspace == null) {
      throw new IllegalStateException("run with bazel run");
    }
    var javadoc = SchemaJavadoc.fromProperties();
    var changed = 0;
    for (var source : SchemaJavadoc.sources()) {
      var file = Path.of(workspace).resolve(source);
      var before = Files.readString(file, StandardCharsets.UTF_8);
      var unit = LexicalPreservingPrinter.setup(SchemaJavadoc.parse(before));
      javadoc.apply(unit);
      var after = LexicalPreservingPrinter.print(unit);
      if (!after.equals(before)) {
        Files.writeString(file, after, StandardCharsets.UTF_8);
        changed++;
      }
    }
    System.out.println(changed + " sources' Javadoc rewritten from the schema");
  }
}
