// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;

/**
 * Writes the converter's expected output for every fixture in scope into
 * formex-converter/src/test/resources/expected, and removes the files of fixtures that are gone.
 * Run it after a deliberate change to the converter, then review the diff: {@code mise run
 * expected}.
 */
public final class UpdateExpected {

  private UpdateExpected() {}

  public static void main(String[] args) throws IOException, SaxonApiException {
    var workspace = System.getenv("BUILD_WORKSPACE_DIRECTORY");
    if (workspace == null) {
      throw new IllegalStateException("run with bazel run (mise run expected)");
    }
    var out = Path.of(workspace, "formex-converter", "src", "test", "resources", "expected");
    var saxon = new Processor(false);
    var converter = new FormexConverter(saxon);
    var written = new HashSet<Path>();
    for (var fixture : Manifestations.fixtures().toList()) {
      var file = out.resolve(fixture.name() + ".xml");
      Files.createDirectories(file.getParent());
      var legalText = converter.convert(fixture.source(), fixture.celex());
      Files.writeString(file, ExpectedOutput.serialize(saxon, legalText), StandardCharsets.UTF_8);
      written.add(file);
    }
    try (var files = Files.walk(out)) {
      files
          .filter(f -> f.toString().endsWith(".xml") && !written.contains(f))
          .forEach(UpdateExpected::delete);
    }
    System.out.println(written.size() + " expected outputs in " + out);
  }

  private static void delete(Path file) {
    try {
      Files.delete(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
