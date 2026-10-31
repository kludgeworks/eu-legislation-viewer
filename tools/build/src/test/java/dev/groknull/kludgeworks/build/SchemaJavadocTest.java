// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaJavadocTest {
  static final String SCHEMA =
      """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xs:complexType name="InlineNode">
          <xs:annotation><xs:documentation>Anything inside running text.</xs:documentation></xs:annotation>
        </xs:complexType>
        <xs:complexType name="Emphasis">
          <xs:annotation><xs:documentation>Text set in a typeface.</xs:documentation></xs:annotation>
          <xs:attribute name="kind">
            <xs:annotation><xs:documentation>Which typeface.</xs:documentation></xs:annotation>
          </xs:attribute>
        </xs:complexType>
        <xs:simpleType name="EmphasisKind">
          <xs:restriction base="xs:string">
            <xs:enumeration value="b">
              <xs:annotation><xs:documentation>Bold.</xs:documentation></xs:annotation>
            </xs:enumeration>
          </xs:restriction>
        </xs:simpleType>
      </xs:schema>
      """;

  static final String SOURCE =
      """
      package records;

      public sealed interface Inline {
        record Emphasis(Kind kind) implements Inline {
          public enum Kind {
            B
          }
        }
      }
      """;

  @TempDir Path dir;

  @Test
  void documentsRecordsAndEnumsNestedInAnInterface() throws IOException {
    var schema = Files.writeString(dir.resolve("schema.xsd"), SCHEMA);
    var javadoc =
        new SchemaJavadoc(
            new Processor(false),
            schema,
            Map.of("Inline", "InlineNode", "Emphasis.Kind", "EmphasisKind"),
            Map.of());
    var file = SchemaJavadoc.parse(SOURCE);

    assertThat(javadoc.differences(file))
        .containsExactly(
            "Inline: the schema says «Anything inside running text.»",
            "Emphasis: the schema says «Text set in a typeface. @param kind Which typeface.»",
            "Emphasis.Kind.B: the schema says «Bold.»");

    javadoc.apply(file);

    assertThat(javadoc.differences(file)).isEmpty();
  }
}
