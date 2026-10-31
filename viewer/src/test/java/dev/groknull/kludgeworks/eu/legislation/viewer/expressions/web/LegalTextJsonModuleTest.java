// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.PreambleContent;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.TableRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

/** The legal text's JSON: as the golden files have it, and in legal-text.xsd's names. */
class LegalTextJsonModuleTest {
  static final Pattern ELEMENT = Pattern.compile("<xs:element name=\"(\\w+)\" type=\"(\\w+)\"");
  static final Pattern ENUMERATION = Pattern.compile("<xs:enumeration value=\"(\\w+)\"");

  static final List<String> GOLDEN = LegalTextJson.GOLDEN;

  @ParameterizedTest
  @FieldSource("GOLDEN")
  void aTextIsWrittenAsItsGoldenJson(String name) throws IOException {
    var golden =
        LegalTextJson.golden(
            LegalTextJson.runfile("kludgeworks_viewer/viewer/src/test/resources/legal-text-json"),
            name);

    assertThat(LegalTextJson.render(name))
        .as("%s; after a deliberate change: mise run expected:json, and review the diff", golden)
        .isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
  }

  @Test
  void everyCaseIsTypedByAnElementOfItsTypeInTheSchema() throws IOException {
    var elements = new HashSet<String>();
    var matcher = ELEMENT.matcher(schema());
    while (matcher.find()) {
      elements.add(matcher.group(1) + "=" + matcher.group(2));
    }

    Stream.of(Block.class, Inline.class, PreambleContent.class, TableRow.class)
        .flatMap(LegalTextJsonModuleTest::cases)
        .forEach(
            type ->
                assertThat(elements)
                    .as(type.getName())
                    .contains(LegalTextJsonModule.ELEMENTS.get(type) + "=" + type.getSimpleName()));
  }

  @Test
  void everyEnumerationIsWrittenAsAValueOfTheSchema() throws IOException {
    var values = new HashSet<String>();
    var matcher = ENUMERATION.matcher(schema());
    while (matcher.find()) {
      values.add(matcher.group(1));
    }

    var enums = LegalTextJsonModule.model().stream().filter(Class::isEnum).toList();
    assertThat(enums).isNotEmpty();
    for (var type : enums) {
      for (var constant : type.getEnumConstants()) {
        assertThat(values)
            .as("%s.%s", type.getName(), constant)
            .contains(LegalTextJson.JSON.valueToTree(constant).asString());
      }
    }
  }

  /** The records a sealed choice can be, through the choices it holds. */
  private static Stream<Class<?>> cases(Class<?> type) {
    return type.isSealed()
        ? Arrays.stream(type.getPermittedSubclasses()).flatMap(LegalTextJsonModuleTest::cases)
        : Stream.of(type);
  }

  private static String schema() throws IOException {
    return Files.readString(LegalTextJson.runfile(LegalTextJson.SCHEMA), StandardCharsets.UTF_8);
  }
}
