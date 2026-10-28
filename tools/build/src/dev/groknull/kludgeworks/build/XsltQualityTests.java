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
 * Checks each stylesheet in {@code xslt-quality.files} (set by xslt_quality_test, separated by
 * spaces) with xslt-quality, one dynamic test each: any finding fails it. The validator is built
 * once, by the xslt_quality_validator genrule; this only runs it.
 */
public class XsltQualityTests {

  @TestFactory
  Stream<DynamicTest> stylesheets() throws Exception {
    var quality = XsltQuality.load(new Processor(false));
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
}
