// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.descendants;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Tests that replaced, inserted, and deleted images show old and new figures rather than a pixel
 * diff.
 */
class FigureEditsTest {

  @Test
  void replacedFigureInOccurrenceEmitsOldInDelAndNewInIns() {
    var before = occurrence(figure("old.tif", "Diagram"));
    var after = occurrence(figure("new.tif", "Diagram"));

    var edited = OccurrenceEdits.of(before, after);

    assertThat(edited).isNotNull();
    var rendered = edited.blocks().of(after.blocks());
    assertThat(rendered).hasSize(2);
    assertThat(rendered.get(0).name()).isEqualTo("diff:del");
    assertThat(rendered.get(0).children().getFirst().attribute("src")).isEqualTo("old.tif");
    assertThat(rendered.get(1).name()).isEqualTo("diff:ins");
    assertThat(rendered.get(1).children().getFirst().attribute("src")).isEqualTo("new.tif");
  }

  @Test
  void figureWithUnchangedImageDiffsOnlyItsCaptionWords() {
    var before = figure("same.tif", "Diagram 1");
    var after = figure("same.tif", "Diagram 2");

    var edited = Edits.between(before, after).element();

    assertThat(edited.name()).isEqualTo("figure");
    assertThat(edited.attribute("src")).isEqualTo("same.tif");
    var dels = descendants(edited).stream().filter(el -> el.name().equals("diff:del")).toList();
    var inss = descendants(edited).stream().filter(el -> el.name().equals("diff:ins")).toList();
    assertThat(dels).hasSize(1);
    assertThat(inss).hasSize(1);
    assertThat(dels.getFirst().children().getFirst().attribute("v")).isEqualTo("1");
    assertThat(inss.getFirst().children().getFirst().attribute("v")).isEqualTo("2");
  }

  @Test
  void replacedInlineImgEmitsDeletedAndInsertedImages() {
    var before = new Element("p", Map.of(), List.of(img("a.tif")), "");
    var after = new Element("p", Map.of(), List.of(img("b.tif")), "");

    var edited = Edits.between(before, after).element();

    var dels = descendants(edited).stream().filter(el -> el.name().equals("diff:del")).toList();
    var inss = descendants(edited).stream().filter(el -> el.name().equals("diff:ins")).toList();
    assertThat(dels).hasSize(1);
    assertThat(inss).hasSize(1);
    assertThat(dels.getFirst().children().getFirst().attribute("src")).isEqualTo("a.tif");
    assertThat(inss.getFirst().children().getFirst().attribute("src")).isEqualTo("b.tif");
  }

  @Test
  void replacedFigureWithChangedCaptionInQuotationDoesNotDropOrDuplicateCaption() {
    var before =
        new Element("quotedStructure", Map.of(), List.of(figure("old.tif", "Diagram 1")), "");
    var after =
        new Element("quotedStructure", Map.of(), List.of(figure("new.tif", "Diagram 2")), "");

    var edited = Edits.between(before, after).element();

    assertThat(edited.children()).hasSize(2);
    assertThat(edited.children().get(0).name()).isEqualTo("diff:del");
    assertThat(edited.children().get(0).children()).hasSize(1);
    var delFigure = edited.children().get(0).children().getFirst();
    assertThat(delFigure.attribute("src")).isEqualTo("old.tif");
    assertThat(BuiltTexts.said(delFigure)).contains("Diagram 1");
    assertThat(
            descendants(delFigure).stream()
                .filter(el -> el.name().equals("caption"))
                .map(BuiltTexts::said))
        .containsExactly("Diagram 1");
    assertThat(edited.children().get(1).name()).isEqualTo("diff:ins");
    assertThat(edited.children().get(1).children()).hasSize(1);
    var insFigure = edited.children().get(1).children().getFirst();
    assertThat(insFigure.attribute("src")).isEqualTo("new.tif");
    assertThat(
            descendants(insFigure).stream()
                .filter(el -> el.name().equals("caption"))
                .map(BuiltTexts::said))
        .containsExactly("Diagram 2");
  }

  @Test
  void replacedFigureCountsWordsInCaptionAndDescription() {
    var before =
        new Element(
            "figure",
            Map.of("src", "old.tif", "content", "techDesign"),
            List.of(
                new Element(
                    "caption",
                    Map.of(),
                    List.of(
                        new Element(
                            "p",
                            Map.of(),
                            List.of(new Element("t", Map.of("v", "Old diagram"), List.of(), "")),
                            "")),
                    ""),
                new Element(
                    "description",
                    Map.of(),
                    List.of(
                        new Element(
                            "p",
                            Map.of(),
                            List.of(
                                new Element(
                                    "t", Map.of("v", "Three deleted words"), List.of(), "")),
                            "")),
                    "")),
            "");
    var after = figure("new.tif", "New diagram");

    var edited = OccurrenceEdits.of(occurrence(before), occurrence(after));

    assertThat(edited).isNotNull();
    assertThat(edited.blocks().deleted()).isEqualTo(6);
    assertThat(edited.blocks().inserted()).isEqualTo(3);

    var inParagraph =
        Edits.between(
            new Element("p", Map.of(), List.of(before), ""),
            new Element("div", Map.of(), List.of(after), ""));
    assertThat(inParagraph.deleted()).isEqualTo(6);
    assertThat(inParagraph.inserted()).isEqualTo(3);
  }

  @Test
  void figureWithChangedCaptionInQuotationEditsOnlyCaptionWords() {
    var before =
        new Element("quotedStructure", Map.of(), List.of(figure("same.tif", "Diagram 1")), "");
    var after =
        new Element("quotedStructure", Map.of(), List.of(figure("same.tif", "Diagram 2")), "");

    var edited = Edits.between(before, after).element();

    assertThat(edited.children()).hasSize(1);
    var figure = edited.children().getFirst();
    assertThat(figure.name()).isEqualTo("figure");
    assertThat(figure.attribute("src")).isEqualTo("same.tif");
    var dels = descendants(figure).stream().filter(el -> el.name().equals("diff:del")).toList();
    var inss = descendants(figure).stream().filter(el -> el.name().equals("diff:ins")).toList();
    assertThat(dels).hasSize(1);
    assertThat(inss).hasSize(1);
    assertThat(dels.getFirst().children().getFirst().attribute("v")).isEqualTo("1");
    assertThat(inss.getFirst().children().getFirst().attribute("v")).isEqualTo("2");
  }

  private static Occurrence occurrence(Element block) {
    var occ =
        new Element("occurrence", Map.of("eId", "art_1", "kind", "article"), List.of(block), "");
    var root = new Element("legalText", Map.of("kind", "act", "xml:lang", "en"), List.of(occ), "");
    return Objects.requireNonNull(Occurrences.of(root).byEId().get("art_1"));
  }

  private static Element figure(String src, String captionText) {
    var caption =
        new Element(
            "caption",
            Map.of(),
            List.of(
                new Element(
                    "p",
                    Map.of(),
                    List.of(new Element("t", Map.of("v", captionText), List.of(), "")),
                    "")),
            "");
    return new Element("figure", Map.of("src", src, "content", "techDesign"), List.of(caption), "");
  }

  private static Element img(String src) {
    return new Element("img", Map.of("src", src, "content", "other"), List.of(), "");
  }
}
