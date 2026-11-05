// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 2015/1998's consolidations of 1 January, 22 March and 1 October 2026 compared, as the content
 * module reads them back: 2026/449 applies from 22 March, its points on screening in-flight and
 * airport supplies from 1 October. A marked element is written out as said: its words, those
 * inserted [+so], those deleted [-so].
 */
class DifferTest {
  static final Element JANUARY = text("02015R1998-20260101");
  static final Element MARCH = text("02015R1998-20260322");
  static final Element OCTOBER = text("02015R1998-20261001");

  @Test
  void marksTheWordsAnActInsertedInEachPointItChanged() {
    var differences = Differ.differences(MARCH, OCTOBER);
    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(
            tuple("anx/pnt_8.1.2.3/pnt_c", Kind.CHANGED),
            tuple("anx/pnt_8.1.2.3/pnt_e", Kind.CHANGED),
            tuple("anx/pnt_8.1.2.3/pnt_g", Kind.CHANGED),
            tuple("anx/pnt_9.1.2.3/pnt_c", Kind.CHANGED),
            tuple("anx/pnt_9.1.2.3/pnt_e", Kind.CHANGED),
            tuple("anx/pnt_9.1.2.3/pnt_g", Kind.CHANGED));
    var c = differences.getFirst();
    assertThat(said(only(c.shown(), "p")))
        .isEqualTo(
            "x-ray equipment[+ applied in accordance with Attachment 6-J to the Annex to"
                + " Implementing Decision C(2015) 8005];");
    assertThat(said(only(c.shown(), "num"))).isEqualTo("(c)");
    assertThat(c.inserted()).isEqualTo(16);
    assertThat(c.deleted()).isZero();
  }

  @Test
  void replacesAPhraseAsOneDeletionAndOneInsertion() {
    var g = Differ.differences(MARCH, OCTOBER).get(2);
    assertThat(said(only(g.shown(), "p")))
        .isEqualTo(
            "EVD equipment applied in accordance with[+ Attachment 6-J to] the [-relevant"
                + " provisions contained in Attachment 6-J][+Annex to Implementing Decision C(2015)"
                + " 8005,] and in combination with point (a)[+ of this point].");
    assertThat(g.inserted()).isEqualTo(14);
    assertThat(g.deleted()).isEqualTo(7);
  }

  @Test
  void keepsAPhraseInsertedWholeThoughItRepeatsAWordAfterIt() {
    var e = Differ.differences(MARCH, OCTOBER).get(1);
    assertThat(said(only(e.shown(), "p")))
        .isEqualTo(
            "ETD equipment[+ applied in accordance with Attachment 6-J to the Annex to Implementing"
                + " Decision C(2015) 8005, and] in combination with point (a)[+ of this point];");
  }

  @Test
  void keepsTheTextsStructureAroundItsMarks() {
    var c = Differ.differences(MARCH, OCTOBER).getFirst().shown();
    assertThat(c.children()).extracting(Element::name).containsExactly("provenance", "num", "p");
    assertThat(descendants(c))
        .filteredOn(element -> element.name().startsWith("diff:"))
        .allSatisfy(
            mark -> assertThat(mark.children()).extracting(Element::name).containsOnly("t"));
  }

  @Test
  void leadsADifferenceWithTheOpeningOfTheUnitItIsIn() {
    var c = Differ.differences(MARCH, OCTOBER).getFirst();
    assertThat(c.parent()).isEqualTo("anx/pnt_8.1.2.3");
    assertThat(Objects.requireNonNull(c.lead()).children())
        .extracting(Wording::of)
        .containsExactly(
            "8.1.2.3",
            "The following means or method of screening, either individually or in combination,"
                + " shall be applied:");
  }

  @Test
  void foldsTheUnitsBeforeADifferenceThatAreTheSame() {
    var differences = Differ.differences(MARCH, OCTOBER);
    assertThat(differences.get(0).folded()).extracting(Sibling::num).containsExactly("(a)", "(b)");
    assertThat(differences.get(1).folded()).extracting(Sibling::num).containsExactly("(d)");
    assertThat(differences.get(2).folded()).extracting(Sibling::num).containsExactly("(f)");
    assertThat(differences.get(2).folded())
        .extracting(Sibling::eId, Sibling::kind)
        .containsExactly(tuple("anx/pnt_8.1.2.3/pnt_f", "point"));
  }

  @Test
  void showsAUnitInsertedOrDeletedOnceWholeWithItsOwnUnits() {
    var differences = Differ.differences(JANUARY, MARCH);
    assertThat(differences).filteredOn(d -> d.kind() == Kind.INSERTED).hasSize(38);
    assertThat(differences).filteredOn(d -> d.kind() == Kind.DELETED).hasSize(16);
    assertThat(differences).filteredOn(d -> d.kind() == Kind.CHANGED).hasSize(38);
    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .contains(tuple("anx/pnt_8.1.2.4", Kind.INSERTED), tuple("anx/pnt_9.1.3.6", Kind.DELETED))
        .containsSubsequence(
            tuple("anx/pnt_1.2.2.3", Kind.CHANGED),
            tuple("anx/pnt_1.2.2.3/pnt_a", Kind.INSERTED),
            tuple("anx/pnt_1.2.2.3/pnt_b", Kind.INSERTED),
            tuple("anx/pnt_1.2.2.3/pnt_c", Kind.INSERTED))
        .containsSubsequence(
            tuple("anx/pnt_9.1.3.5", Kind.CHANGED),
            tuple("anx/pnt_9.1.3.5/pnt_a", Kind.DELETED),
            tuple("anx/pnt_9.1.3.5/pnt_b", Kind.DELETED),
            tuple("anx/pnt_9.1.3.6", Kind.DELETED));
    assertThat(differences)
        .extracting(Difference::eId)
        .doesNotContain("anx/pnt_9.1.3.5/pnt_b/idt_1");
    var inserted = find(differences, "anx/pnt_8.1.2.4");
    assertThat(unmarked(inserted.shown())).isBlank();
    assertThat(said(inserted.shown())).startsWith("[+").doesNotContain("[-");
    assertThat(inserted.deleted()).isZero();
    var deleted = find(differences, "anx/pnt_9.1.3.6");
    assertThat(unmarked(deleted.shown())).isBlank();
    assertThat(said(deleted.shown())).startsWith("[-").doesNotContain("[+");
    assertThat(deleted.inserted()).isZero();
    assertThat(deleted.folded()).isEmpty();
  }

  @Test
  void showsAChangedUnitWithItsOwnContentOnly() {
    var changed = find(Differ.differences(JANUARY, MARCH), "anx/pnt_1.2.2.3");
    assertThat(descendants(changed.shown())).extracting(Element::name).doesNotContain("unit");
  }

  @Test
  void findsNothingBetweenATextAndItself() {
    assertThat(Differ.differences(MARCH, MARCH)).isEmpty();
  }

  private static Difference find(List<Difference> differences, String eId) {
    return differences.stream().filter(d -> d.eId().equals(eId)).findFirst().orElseThrow();
  }

  /** {@code unit}'s one child named {@code name}. */
  private static Element only(Element unit, String name) {
    return unit.children().stream()
        .filter(child -> child.name().equals(name))
        .reduce(
            (one, other) -> {
              throw new AssertionError("More than one " + name + " in " + unit.attribute("eId"));
            })
        .orElseThrow();
  }

  /** What {@code element} says, its marks written out: [+inserted], [-deleted]. */
  private static String said(Element element) {
    var out = new StringBuilder();
    say(element, out);
    return out.toString();
  }

  private static void say(Element element, StringBuilder out) {
    switch (element.name()) {
      case "t" -> out.append(element.attribute("v"));
      case "diff:ins" -> {
        out.append("[+");
        element.children().forEach(child -> say(child, out));
        out.append(']');
      }
      case "diff:del" -> {
        out.append("[-");
        element.children().forEach(child -> say(child, out));
        out.append(']');
      }
      default -> element.children().forEach(child -> say(child, out));
    }
  }

  /** The words of {@code element} that are in no mark. */
  private static String unmarked(Element element) {
    if (element.name().startsWith("diff:")) {
      return "";
    }
    if (element.name().equals("t")) {
      return Objects.requireNonNullElse(element.attribute("v"), "");
    }
    var out = new StringBuilder();
    element.children().forEach(child -> out.append(unmarked(child)));
    return out.toString();
  }

  private static List<Element> descendants(Element element) {
    return element.children().stream()
        .flatMap(child -> Stream.concat(Stream.of(child), descendants(child).stream()))
        .toList();
  }

  private static Element text(String celex) {
    return Decoder.element(Encoder.encode(LegalTexts.sample("06.02.1/" + celex + ".xml")));
  }
}
