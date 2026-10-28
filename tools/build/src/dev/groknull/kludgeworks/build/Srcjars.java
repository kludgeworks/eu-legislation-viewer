// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Zip a directory of generated sources into a .srcjar with fixed timestamps (reproducible). */
final class Srcjars {
  private Srcjars() {}

  static void zip(Path dir, Path srcjar, String suffix) throws IOException {
    try (var zip = new ZipOutputStream(Files.newOutputStream(srcjar));
        var files = Files.walk(dir)) {
      for (var p :
          files
              .filter(Files::isRegularFile)
              .filter(f -> f.toString().endsWith(suffix))
              .sorted()
              .toList()) {
        var e = new ZipEntry(dir.relativize(p).toString().replace('\\', '/'));
        e.setTime(315532800000L); // 1980-01-01, like Bazel's own zips
        zip.putNextEntry(e);
        Files.copy(p, zip);
        zip.closeEntry();
      }
    }
  }
}
