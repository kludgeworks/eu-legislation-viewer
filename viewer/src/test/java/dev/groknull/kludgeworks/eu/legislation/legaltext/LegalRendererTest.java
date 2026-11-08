// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.legaltext;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the templates of {@link LegalRenderer} on the converter's samples. Every element of
 * legal-text has a template. No template adds or loses a space in running text. Templates often
 * have this bug. For example, the last newline of a template can give "Brussels ,".
 */
class LegalRendererTest {
  private static final TemplateEngine TEMPLATES =
      TemplateEngine.createPrecompiled(ContentType.Html);
  private static final LegalRenderer HTML = new LegalRenderer(TEMPLATES);

  /**
   * The elements that hold running text. A reader reads each of them as one run, and the test
   * checks its text.
   */
  private static final Set<String> RUNNING = Set.of("p", "num", "heading", "subheading");

  @Test
  void hasATemplateForEveryElementOfLegalText() throws Exception {
    var schema =
        LegalTexts.SAXON.newDocumentBuilder().build(new File(System.getProperty("legal-text.xsd")));
    var names =
        LegalTexts.SAXON
            .newXPathCompiler()
            .evaluate("distinct-values(//*[local-name() = 'element']/@name)", schema)
            .stream()
            .map(name -> name.getStringValue())
            .toList();

    assertThat(names).hasSizeGreaterThan(40);
    assertThat(names)
        .allSatisfy(
            name -> assertThat(TEMPLATES.hasTemplate(LegalRenderer.template(name))).isTrue());
  }

  static Stream<String> samples() {
    return LegalTexts.samples();
  }

  @ParameterizedTest
  @MethodSource("samples")
  void rendersRunningTextAsItReads(String sample) {
    var text = Decoder.element(Encoder.encode(LegalTexts.sample(sample)));
    assertThat(render(text)).startsWith("<article class=\"legal-text legal-text-");

    var wrong = new ArrayList<String>();
    for (var run : runs(text, new ArrayList<>())) {
      var read = normalize(Jsoup.parse(render(run)).body().wholeText());
      var expected = normalize(plain(run));
      if (!read.equals(expected)) {
        wrong.add(run.name() + ": " + read + " | expected: " + expected);
      }
    }
    assertThat(wrong).isEmpty();
  }

  private static String render(Element element) {
    var out = new StringOutput();
    HTML.element(element).writeTo(out);
    return out.toString();
  }

  /**
   * The outermost runs of running text in {@code element}. There are no runs in what an amendment
   * deleted, because the Read mode shows only its mark.
   */
  private static List<Element> runs(Element element, List<Element> out) {
    if (element.name().equals("deleted")) {
      return out;
    }
    if (RUNNING.contains(element.name())
        && element.children().stream().noneMatch(c -> c.name().equals("content"))) {
      out.add(element);
    } else {
      element.children().forEach(child -> runs(child, out));
    }
    return out;
  }

  /** What a reader reads in {@code element}. That is its text and what the page prints for it. */
  private static String plain(Element element) {
    return switch (element.name()) {
      case "t" -> Objects.requireNonNull(element.attribute("v"));
      case "number" -> HTML.thousands(Objects.requireNonNull(element.attribute("v")));
      case "quote" ->
          element.attribute("startQuote") + children(element) + element.attribute("endQuote");
      case "noteRef" -> " (" + element.attribute("marker") + ")";
      case "eol" -> " ";
      // The page shows only the mark of a deletion, with no text.
      case "deleted" -> "";
      default -> element.text() + children(element);
    };
  }

  /** What a reader reads in the children of {@code element}, in order. */
  private static String children(Element element) {
    var out = new StringBuilder();
    element.children().forEach(child -> out.append(plain(child)));
    return out.toString();
  }

  /**
   * Whitespace as a browser shows it. Each run of HTML whitespace becomes one space, and there is
   * none at the ends.
   */
  private static String normalize(String text) {
    return text.replaceAll("[ \\t\\n\\r\\f]+", " ").strip();
  }
}
