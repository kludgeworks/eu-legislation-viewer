// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generated sources as a source jar, the form java_library takes them in: what the generators
 * (JooqCodegen, JteGenerate) write in a directory, packed so that the same sources make the same
 * jar, byte for byte, and Bazel's cache holds.
 */
final class SourceJar {
  /** A fixed time for every entry: 1980-01-01, the earliest a zip can record. */
  private static final FileTime EPOCH = FileTime.fromMillis(315_532_800_000L);

  private SourceJar() {}

  /** A temporary directory to generate into, beside the jar. */
  static Path directoryFor(Path jar) throws IOException {
    var parent = jar.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    return Files.createTempDirectory(parent, jar.getFileName() + ".");
  }

  /** Packs the .java files under {@code directory} into {@code jar}, then deletes the directory. */
  static void write(Path directory, Path jar) throws IOException {
    List<Path> sources;
    try (Stream<Path> files = Files.walk(directory)) {
      sources =
          files
              .filter(file -> file.toString().endsWith(".java"))
              .sorted(Comparator.comparing(file -> directory.relativize(file).toString()))
              .toList();
    }
    try (var out = new ZipOutputStream(Files.newOutputStream(jar))) {
      for (var source : sources) {
        var name =
            directory
                .relativize(source)
                .toString()
                .replace(source.getFileSystem().getSeparator(), "/");
        var entry = new ZipEntry(name);
        entry.setLastModifiedTime(EPOCH);
        out.putNextEntry(entry);
        Files.copy(source, out);
        out.closeEntry();
      }
    }
    try (Stream<Path> files = Files.walk(directory)) {
      for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
  }
}
