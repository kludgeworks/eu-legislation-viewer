// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks each stylesheet with xslt-quality, one dynamic test each: any finding fails it. Set by
 * xslt_quality_test:
 *
 * <ul>
 *   <li>{@code xslt-quality.files}: the stylesheets, separated by spaces;
 *   <li>{@code xslt-quality.schema}: xslt-quality's src/main/sch/xslt-quality.sch, in the
 *       repository that holds the sources to build;
 *   <li>{@code xslt-quality.rejected}: the rules switched off, each with its reason.
 * </ul>
 */
public class XsltQualityTests {

  @TestFactory
  Stream<DynamicTest> stylesheets() throws Exception {
    // src/main/sch/xslt-quality.sch → the repository
    var sources = path("xslt-quality.schema").getParent().getParent().getParent().getParent();
    var quality = XsltQuality.build(new Processor(false), sources, path("xslt-quality.rejected"));
    return Arrays.stream(System.getProperty("xslt-quality.files").trim().split("\\s+"))
        .map(Path::of)
        .map(
            stylesheet ->
                DynamicTest.dynamicTest(
                    stylesheet.getFileName().toString(),
                    stylesheet.toUri(),
                    () -> {
                      var findings = quality.check(stylesheet);
                      if (!findings.isEmpty()) {
                        fail(
                            findings.stream()
                                .map(Object::toString)
                                .collect(
                                    Collectors.joining(
                                        "\n  ", findings.size() + " findings:\n  ", "")));
                      }
                    }));
  }

  private static Path path(String property) {
    return Path.of(System.getProperty(property)).toAbsolutePath().normalize();
  }
}
