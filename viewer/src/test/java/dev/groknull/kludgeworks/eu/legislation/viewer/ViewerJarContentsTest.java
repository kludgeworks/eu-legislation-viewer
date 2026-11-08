// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

/** The executable jar's structure and contents, without starting the application or Docker. */
@AutoBazelRepository
@NullMarked
class ViewerJarContentsTest {

  private static final String JAR =
      "_main/viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/viewer-app.jar";

  @Test
  void packagesTheApplicationAndItsProductionResources() throws IOException {
    try (var app = new ZipFile(Path.of(runfiles().rlocation(JAR)).toFile())) {
      var entries = app.stream().map(entry -> entry.getName()).toList();
      assertThat(entries)
          .contains(
              "META-INF/MANIFEST.MF",
              "org/springframework/boot/loader/launch/JarLauncher.class",
              "BOOT-INF/classes/dev/groknull/kludgeworks/eu/legislation/viewer/ViewerApplication.class",
              "BOOT-INF/lib/viewer-resources.jar");
      var libraries =
          entries.stream()
              .filter(name -> name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar"))
              .toList();
      assertThat(new HashSet<>(libraries)).hasSameSizeAs(libraries);
      assertThat(libraries)
          .noneMatch(
              name ->
                  name.contains("junit")
                      || name.contains("mockito")
                      || name.contains("assertj")
                      || name.contains("testcontainers"));

      try (var resources =
          new ZipInputStream(
              app.getInputStream(app.getEntry("BOOT-INF/lib/viewer-resources.jar")))) {
        assertThat(entries(resources))
            .contains(
                "application.yaml",
                "db/migration/__root/V2026_11_0_001__event_publication.sql",
                "db/migration/catalogue/V2026_11_0_001__catalogue.sql",
                "static/css/chrome.css",
                "static/js/clock.js");
      }
    }
  }

  private static Runfiles runfiles() throws IOException {
    return Runfiles.preload().withSourceRepository(AutoBazelRepository_ViewerJarContentsTest.NAME);
  }

  private static List<String> entries(ZipInputStream archive) throws IOException {
    var names = new ArrayList<String>();
    for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
      names.add(entry.getName());
    }
    return names;
  }
}
