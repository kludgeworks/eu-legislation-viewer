// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.loader.tools.Library;
import org.springframework.boot.loader.tools.LibraryScope;
import org.springframework.boot.loader.tools.Repackager;

/**
 * A Spring Boot executable jar from Bazel's jars, by the Repackager Spring Boot's Maven and Gradle
 * plugins use: the application's jar with its launcher, the other jars nested in BOOT-INF/lib, each
 * keeping its own META-INF (which a merged jar can't: its auto-configuration lists would collide).
 *
 * <p>Arguments: the jar to write, the main class, the application's jar, then each other jar:
 * {@code --local <name> <jar>} for ours, named by the rule, or {@code --library <jar>} for a
 * dependency's (Boot's layers keep them apart). An argument {@code @file} stands for the file's
 * lines.
 */
public final class SpringBootJar {

  /** Every entry's time, so the jar depends on its inputs only (Maven's outputTimestamp). */
  private static final FileTime ENTRY_TIME = FileTime.from(Instant.parse("1980-02-01T00:00:00Z"));

  private SpringBootJar() {}

  public static void main(String[] args) throws IOException {
    var arguments = expand(args);
    var out = new File(arguments.get(0));
    var repackager = new Repackager(new File(arguments.get(2)));
    repackager.setMainClass(arguments.get(1));
    repackager.setBackupSource(false); // the application's jar is Bazel's, read-only
    repackager.setIncludeRelevantJarModeJars(false); // no jar mode tools on the classpath
    var libraries = new ArrayList<Library>();
    var names = new HashSet<String>();
    var i = 3;
    while (i < arguments.size()) {
      var local =
          switch (arguments.get(i)) {
            case "--local" -> true;
            case "--library" -> false;
            default ->
                throw new IllegalArgumentException("not --local or --library: " + arguments.get(i));
          };
      // rules_jvm_external's copies are processed_<artifact>-<version>.jar
      var file = new File(arguments.get(local ? i + 2 : i + 1));
      var name = local ? arguments.get(i + 1) : file.getName().replaceFirst("^processed_", "");
      i += local ? 3 : 2;
      if (!names.add(name)) {
        throw new IllegalArgumentException("two jars named " + name + " in BOOT-INF/lib");
      }
      libraries.add(new Library(name, file, LibraryScope.RUNTIME, null, false, local, true));
    }
    repackager.repackage(
        out,
        callback -> {
          for (var library : libraries) {
            callback.library(library);
          }
        },
        ENTRY_TIME);
  }

  private static List<String> expand(String[] args) throws IOException {
    var expanded = new ArrayList<String>();
    for (var arg : args) {
      if (arg.startsWith("@")) {
        expanded.addAll(Files.readAllLines(Path.of(arg.substring(1))));
      } else {
        expanded.add(arg);
      }
    }
    return expanded;
  }
}
