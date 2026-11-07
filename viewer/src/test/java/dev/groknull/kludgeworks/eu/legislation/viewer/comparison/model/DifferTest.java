// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Differ.Compared;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
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
    var differences = Differ.differences(MARCH, OCTOBER).differences();
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
    var g = Differ.differences(MARCH, OCTOBER).differences().get(2);
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
    var e = Differ.differences(MARCH, OCTOBER).differences().get(1);
    assertThat(said(only(e.shown(), "p")))
        .isEqualTo(
            "ETD equipment[+ applied in accordance with Attachment 6-J to the Annex to Implementing"
                + " Decision C(2015) 8005, and] in combination with point (a)[+ of this point];");
  }

  @Test
  void keepsTheTextsStructureAroundItsMarks() {
    var c = Differ.differences(MARCH, OCTOBER).differences().getFirst().shown();
    assertThat(c.children()).extracting(Element::name).containsExactly("provenance", "num", "p");
    assertThat(descendants(c))
        .filteredOn(element -> element.name().startsWith("diff:"))
        .allSatisfy(
            mark -> assertThat(mark.children()).extracting(Element::name).containsOnly("t"));
  }

  @Test
  void keepsTheListAPointIsAnItemOfWithoutItsItems() {
    var c = Differ.differences(MARCH, OCTOBER).differences().getFirst();
    var list = Objects.requireNonNull(c.list());
    assertThat(list.name()).isEqualTo("list");
    assertThat(list.attribute("kind")).isNotBlank();
    assertThat(list.children()).isEmpty();
  }

  @Test
  void leadsADifferenceWithTheOpeningOfTheUnitItIsIn() {
    var c = Differ.differences(MARCH, OCTOBER).differences().getFirst();
    assertThat(c.parent()).isEqualTo("anx/pnt_8.1.2.3");
    assertThat(Objects.requireNonNull(c.lead()).children())
        .extracting(Wording::of)
        .containsExactly(
            "8.1.2.3",
            "The following means or method of screening, either individually or in combination,"
                + " shall be applied:");
  }

  @Test
  void foldsTheUnitsBetweenTwoDifferencesThatAreTheSame() {
    var differences = Differ.differences(MARCH, OCTOBER).differences();
    assertThat(differences.get(0).context().getLast().units())
        .extracting(Sibling::num)
        .containsExactly("(a)", "(b)");
    assertThat(differences.get(1).context())
        .singleElement()
        .satisfies(fold -> assertThat(fold.opening()).isNull())
        .extracting(Context::units)
        .asInstanceOf(InstanceOfAssertFactories.list(Sibling.class))
        .extracting(Sibling::eId, Sibling::kind, Sibling::num)
        .containsExactly(tuple("anx/pnt_8.1.2.3/pnt_d", "point", "(d)"));
  }

  @Test
  void placesEachUnitOfTheLaterTextOnceInOrder() {
    assertThat(placed(Differ.differences(MARCH, OCTOBER), OCTOBER))
        .containsExactlyElementsOf(Units.of(OCTOBER).byEId().keySet());
    assertThat(placed(Differ.differences(JANUARY, MARCH), MARCH))
        .containsExactlyElementsOf(Units.of(MARCH).byEId().keySet());
  }

  @Test
  void foldsTheUnitsAfterADifferenceToTheNextAndAfterTheLast() {
    var compared = Differ.differences(MARCH, OCTOBER);
    // 8.1.2.3's last, (g), then 8.1.2.4 and on to 9.1.2.3, and 9.1.2.4 and on to the end
    var between = compared.differences().get(3).context();
    assertThat(between.getFirst().units())
        .extracting(Sibling::eId)
        .first()
        .isEqualTo("anx/pnt_8.1.2.4");
    assertThat(between)
        .filteredOn(each -> each.opening() != null)
        .extracting(each -> each.units().getFirst().eId())
        .containsExactly("anx/pnt_9.1.2.3");
    assertThat(compared.closing().getFirst().units())
        .extracting(Sibling::eId)
        .first()
        .isEqualTo("anx/pnt_9.1.2.4");
  }

  @Test
  void opensEachUnitThatHoldsDifferencesOnceWhereTheyEnterIt() {
    var compared = Differ.differences(JANUARY, MARCH);
    var opened =
        Stream.concat(
                compared.differences().stream().flatMap(d -> d.context().stream()),
                compared.closing().stream())
            .filter(each -> each.opening() != null)
            .map(each -> each.units().getFirst().eId())
            .toList();
    assertThat(opened).doesNotHaveDuplicates().contains("anx");
  }

  @Test
  void showsAUnitInsertedOrDeletedOnceWholeWithItsOwnUnits() {
    var differences = Differ.differences(JANUARY, MARCH).differences();
    assertThat(differences).filteredOn(d -> d.kind() == Kind.INSERTED).hasSize(38);
    // and those the later text keeps struck: deleted
    assertThat(differences).filteredOn(d -> d.kind() == Kind.DELETED).hasSize(24);
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
  }

  @Test
  void readsAUnitTheLaterTextKeepsStruckAsDeletedByTheActThatStruckIt() {
    var struck = find(Differ.differences(JANUARY, MARCH).differences(), "anx/pnt_6.8.1.6");
    assertThat(struck.kind()).isEqualTo(Kind.DELETED);
    assertThat(struck.markers()).containsExactly(new Marker("32026R0449", "deleted", null));
    assertThat(Units.of(MARCH).get("anx/pnt_6.8.1.6")).isNull();
  }

  @Test
  void showsAChangedUnitWithItsOwnContentOnly() {
    var changed = find(Differ.differences(JANUARY, MARCH).differences(), "anx/pnt_1.2.2.3");
    assertThat(descendants(changed.shown())).extracting(Element::name).doesNotContain("unit");
  }

  @Test
  void namesTheActsThatMarkAUnitInEachText() {
    var c = Differ.differences(MARCH, OCTOBER).differences().getFirst();
    assertThat(c.markers()).containsExactly(new Marker("32026R0449", "replaced", null));
    // unmarked in March, and in no unit an act put whole: as the act was published
    assertThat(c.earlierMarkers()).isEmpty();
  }

  @Test
  void readsAPointWithoutMarkersAsTheActThatPutThePointItIsInWholePutIt() {
    var units = Units.of(MARCH);
    var a = Objects.requireNonNull(units.get("anx/pnt_1.2.2.3/pnt_a"));
    assertThat(Markers.of(a, units))
        .containsExactly(new Marker("32026R0449", "replaced", "anx/pnt_1.2.2.3"));
  }

  @Test
  void findsNothingBetweenATextAndItself() {
    assertThat(Differ.differences(MARCH, MARCH).differences()).isEmpty();
  }

  /**
   * The eIds of the {@code later} text's units as {@code compared} places them, in order: each
   * difference but a deleted one, each unit opened or folded, and the units in one folded or
   * inserted. A unit placed twice is there twice.
   */
  private static List<String> placed(Compared compared, Element later) {
    var units = Units.of(later);
    var placed = new ArrayList<String>();
    for (var difference : compared.differences()) {
      placed(difference.context(), units, placed);
      if (difference.kind() != Kind.DELETED) {
        placed.add(difference.eId());
      }
      if (difference.kind() == Kind.INSERTED) {
        placed.addAll(inside(difference.eId(), units));
      }
    }
    placed(compared.closing(), units, placed);
    return placed;
  }

  private static void placed(List<Context> context, Units units, List<String> placed) {
    for (var each : context) {
      for (var unit : each.units()) {
        placed.add(unit.eId());
        if (each.opening() == null) {
          placed.addAll(inside(unit.eId(), units));
        }
      }
    }
  }

  /** The eIds of the units in the unit {@code eId}, in document order. */
  private static List<String> inside(String eId, Units units) {
    var inside = new ArrayList<String>();
    for (var unit : units.in(eId)) {
      inside.add(unit);
      inside.addAll(inside(unit, units));
    }
    return inside;
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
