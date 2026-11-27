// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/**
 * Tests image URLs, alternative text, fallback placeholders, and comparison sides in {@link
 * LegalRenderer}.
 */
class LegalRendererImageTest {
  private final LegalRenderer renderer =
      new LegalRenderer(TemplateEngine.createPrecompiled(gg.jte.ContentType.Html));

  @Test
  void rendersFigureAndInlineImgWhenRegisteredAndFallsBackWhenMissing() {
    var registeredFigure =
        new Element(
            "figure",
            Map.of("src", "L_2009330EN.01008102.tif", "content", "form"),
            List.of(
                new Element(
                    "caption",
                    Map.of(),
                    List.of(new Element("p", Map.of(), List.of(t("Form 1")), "")),
                    "")),
            "");
    var unregisteredFigure =
        new Element("figure", Map.of("src", "unregistered.tif", "content", "other"), List.of(), "");
    var inline =
        new Element(
            "p",
            Map.of(),
            List.of(
                new Element(
                    "img", Map.of("src", "sym_1.tif", "content", "character"), List.of(), ""),
                new Element(
                    "img", Map.of("src", "missing.tif", "content", "other"), List.of(), "")),
            "");
    var container =
        new Element(
            "legalText",
            Map.of("kind", "act", "xml:lang", "en"),
            List.of(registeredFigure, unregisteredFigure, inline),
            "");

    var out = new StringOutput();
    renderer
        .forText(new TextId(42), Set.of("L_2009330EN.01008102.tif", "sym_1.tif"), "v1")
        .element(container)
        .writeTo(out);
    var doc = Jsoup.parse(out.toString());

    var img = Objects.requireNonNull(doc.selectFirst("figure.figure img"));
    assertThat(doc.selectFirst("figure.figure a.figure-link")).isNull();
    assertThat(img.attr("src")).isEqualTo("/images/42/L_2009330EN.01008102.tif?v=v1");
    assertThat(img.attr("alt")).isEqualTo("Form");
    var caption = Objects.requireNonNull(doc.selectFirst("figure.figure figcaption"));
    assertThat(caption.text()).isEqualTo("Form 1");

    var fallbackFigure =
        Objects.requireNonNull(doc.selectFirst("figure[data-src='unregistered.tif']"));
    assertThat(fallbackFigure.selectFirst("img")).isNull();

    var inlineImg = Objects.requireNonNull(doc.selectFirst("p img.img"));
    assertThat(inlineImg.attr("src")).isEqualTo("/images/42/sym_1.tif?v=v1");
    assertThat(inlineImg.attr("alt")).isEqualTo("Character");
    assertThat(doc.selectFirst("p span.img[data-src='missing.tif']")).isNotNull();
  }

  @Test
  void resolvesDeletedImagesAgainstBeforeTextAndInsertedImagesAgainstAfterText() {
    var oldFigure =
        new Element("figure", Map.of("src", "old.tif", "content", "other"), List.of(), "");
    var newFigure =
        new Element("figure", Map.of("src", "new.tif", "content", "techDesign"), List.of(), "");
    var del = new Element("diff:del", Map.of(), List.of(oldFigure), "");
    var ins = new Element("diff:ins", Map.of(), List.of(newFigure), "");
    var block =
        new Element(
            "legalText", Map.of("kind", "consolidated", "xml:lang", "en"), List.of(del, ins), "");

    var out = new StringOutput();
    renderer
        .forComparison(new TextId(10), Set.of("old.tif"), new TextId(20), Set.of("new.tif"), "v1")
        .element(block)
        .writeTo(out);
    var doc = Jsoup.parse(out.toString());

    var oldImg = Objects.requireNonNull(doc.selectFirst("del.diff figure.figure img"));
    assertThat(oldImg.attr("src")).isEqualTo("/images/10/old.tif?v=v1");
    assertThat(oldImg.attr("alt")).isEqualTo("Figure");
    var newImg = Objects.requireNonNull(doc.selectFirst("ins.diff figure.figure img"));
    assertThat(newImg.attr("src")).isEqualTo("/images/20/new.tif?v=v1");
    assertThat(newImg.attr("alt")).isEqualTo("Technical diagram");
  }

  private static Element t(String v) {
    return new Element("t", Map.of("v", v), List.of(), "");
  }
}
