// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The records' Javadoc says what the schema says ({@link SchemaJavadoc}); else the macro's update
 * target rewrites it. The macro passes the schema, the sources and the tables as system properties.
 */
public class SchemaJavadocTests {

  @Test
  void recordsSayWhatTheSchemaSays() throws IOException {
    var javadoc = SchemaJavadoc.fromProperties();
    var differences = new ArrayList<String>();
    for (var source : SchemaJavadoc.sources()) {
      differences.addAll(
          javadoc.differences(
              SchemaJavadoc.parse(Files.readString(source, StandardCharsets.UTF_8))));
    }
    assertTrue(!SchemaJavadoc.sources().isEmpty(), "no sources");
    assertEquals(
        List.of(),
        differences,
        "Javadoc that differs from the schema; run " + System.getProperty("schema-javadoc.update"));
  }
}
