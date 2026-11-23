// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The texts that the tests of the comparison's stages use: built by hand, or the converter's
 * samples as the content module reads them back. It also writes out an element with its edits as it
 * reads: inserted words show as [+so] and deleted words as [-so].
 */
final class BuiltTexts {
  /** The consolidations of 2015/1998 of 1 January, 22 March and 1 October 2026. */
  static final Element JANUARY = text("02015R1998-20260101");

  static final Element MARCH = text("02015R1998-20260322");
  static final Element OCTOBER = text("02015R1998-20261001");

  private BuiltTexts() {}

  /**
   * A point {@code eId} with the words {@code words} and the occurrences {@code occurrences}. The
   * act {@code act} replaced it.
   */
  static Element point(String eId, String words, String act, Element... occurrences) {
    var children = new ArrayList<Element>();
    children.add(
        new Element("tag", Map.of("amendingAct", act, "modification", "replaced"), List.of(), ""));
    children.add(block("p", words));
    children.addAll(List.of(occurrences));
    return new Element("occurrence", Map.of("eId", eId, "kind", "point"), children, "");
  }

  /**
   * A point {@code eId} with the number {@code num}, the words {@code words}, amending act {@code
   * act}, modification {@code modification}, and child occurrences {@code occurrences}.
   */
  static Element pointWithNum(
      String eId,
      String num,
      String words,
      String act,
      String modification,
      Element... occurrences) {
    return pointWithNum(eId, num, words, act, "", modification, occurrences);
  }

  /**
   * A point {@code eId} with the number {@code num}, the words {@code words}, amending act {@code
   * act}, tag id {@code tagId}, modification {@code modification}, and child occurrences {@code
   * occurrences}.
   */
  static Element pointWithNum(
      String eId,
      String num,
      String words,
      String act,
      String tagId,
      String modification,
      Element... occurrences) {
    var children = new ArrayList<Element>();
    if (!act.isEmpty() && !modification.isEmpty() && !"renumbered".equals(modification)) {
      var attrs = new HashMap<String, String>();
      attrs.put("amendingAct", act);
      attrs.put("modification", modification);
      if (!tagId.isEmpty()) {
        attrs.put("tagId", tagId);
      }
      children.add(new Element("tag", attrs, List.of(), ""));
    }
    Element numBlock;
    if ("renumbered".equals(modification)) {
      var attrs = new HashMap<String, String>();
      attrs.put("modification", "renumbered");
      attrs.put("amendingAct", act);
      if (!tagId.isEmpty()) {
        attrs.put("tagId", tagId);
      }
      var changed =
          new Element(
              "changed", attrs, List.of(new Element("t", Map.of("v", num), List.of(), "")), "");
      numBlock = new Element("num", Map.of(), List.of(changed), "");
    } else {
      numBlock = block("num", num);
    }
    children.add(numBlock);
    children.add(block("p", words));
    children.addAll(List.of(occurrences));
    return new Element("occurrence", Map.of("eId", eId, "kind", "point"), children, "");
  }

  /** An annex {@code eId} with tag {@code act} and {@code modification}. */
  static Element annexWithTags(
      String eId,
      String num,
      String words,
      String act,
      String modification,
      Element... occurrences) {
    var children = new ArrayList<Element>();
    if (act != null && modification != null) {
      children.add(
          new Element(
              "tag", Map.of("amendingAct", act, "modification", modification), List.of(), ""));
    }
    children.add(block("num", num));
    if (!words.isEmpty()) {
      children.add(block("p", words));
    }
    children.addAll(List.of(occurrences));
    return new Element("occurrence", Map.of("eId", eId, "kind", "annex"), children, "");
  }

  /** An article with replaced tag and renumbered num. */
  static Element articleWithReplacedAndRenumbered(
      String eId,
      String num,
      String words,
      String replacedAct,
      String renumberedAct,
      Element... occurrences) {
    var children = new ArrayList<Element>();
    if (!replacedAct.isEmpty()) {
      children.add(
          new Element(
              "tag",
              Map.of("amendingAct", replacedAct, "modification", "replaced"),
              List.of(),
              ""));
    }
    Element numBlock;
    if (!renumberedAct.isEmpty()) {
      var changed =
          new Element(
              "changed",
              Map.of("modification", "renumbered", "amendingAct", renumberedAct),
              List.of(new Element("t", Map.of("v", num), List.of(), "")),
              "");
      numBlock = new Element("num", Map.of(), List.of(changed), "");
    } else {
      numBlock = block("num", num);
    }
    children.add(numBlock);
    if (!words.isEmpty()) {
      children.add(block("p", words));
    }
    children.addAll(List.of(occurrences));
    return new Element("occurrence", Map.of("eId", eId, "kind", "article"), children, "");
  }

  /** A paragraph {@code eId} with number {@code num} and words {@code words}. */
  static Element paragraph(String eId, String num, String words, Element... occurrences) {
    var children = new ArrayList<Element>();
    children.add(block("num", num));
    if (!words.isEmpty()) {
      children.add(block("p", words));
    }
    children.addAll(List.of(occurrences));
    return new Element("occurrence", Map.of("eId", eId, "kind", "paragraph"), children, "");
  }

  static Element numbered(String num, String heading) {
    return new Element(
        "occurrence",
        Map.of("eId", "11.3", "kind", "level"),
        List.of(block("num", num), block("heading", heading)),
        "");
  }

  static Element block(String name, String words) {
    return new Element(
        name, Map.of(), List.of(new Element("t", Map.of("v", words), List.of(), "")), "");
  }

  static Element text(Element... occurrences) {
    return new Element("body", Map.of(), List.of(occurrences), "");
  }

  /** Articles 5 and 6. Article 5(1) has a point (a). */
  static Element articlesWithPoint() {
    return text(
        articleWithReplacedAndRenumbered(
            "art_5",
            "Article 5",
            "General rules and framework on transport safety across the Union.",
            "ActA",
            "",
            paragraph(
                "art_5/par_1",
                "1.",
                "Scope.",
                pointWithNum(
                    "art_5/par_1/pnt_a",
                    "(a)",
                    "Deleted point on inspection requirements.",
                    "ActA",
                    "replaced"))),
        articleWithReplacedAndRenumbered(
            "art_6",
            "Article 6",
            "Special security standards and oversight for international ports.",
            "ActA",
            "",
            paragraph("art_6/par_1", "1.", "Security provisions.")));
  }

  /**
   * The articles of {@link #articlesWithPoint}, as Articles 6 and 7. ActX replaced the first,
   * without the point (a), and {@code renumberedBy} renumbered it.
   */
  static Element articlesRenumbered(String renumberedBy) {
    return text(
        articleWithReplacedAndRenumbered(
            "art_6",
            "Article 6",
            "General rules and framework on transport safety across the Union.",
            "ActX",
            renumberedBy,
            paragraph("art_6/par_1", "1.", "Scope.")),
        articleWithReplacedAndRenumbered(
            "art_7",
            "Article 7",
            "Special security standards and oversight for international ports.",
            "WrongAct",
            "ActX",
            paragraph("art_7/par_1", "1.", "Security provisions.")));
  }

  /**
   * A text with the occurrences {@code occurrences} in its body, and the footnotes {@code
   * footnotes} at its end.
   */
  static Element withFootnotes(List<Element> occurrences, Element... footnotes) {
    return new Element(
        "legalText",
        Map.of(),
        List.of(
            new Element("body", Map.of(), occurrences, ""),
            new Element("footnotes", Map.of(), List.of(footnotes), "")),
        "");
  }

  static Element footnote(String eId, String words) {
    return new Element(
        "footnote",
        Map.of("eId", eId, "marker", eId.substring(eId.indexOf('_') + 1), "kind", "footnote"),
        List.of(block("p", words)),
        "");
  }

  /** A block that says {@code words} and refers to the footnote {@code eId}. */
  static Element cites(String words, String eId) {
    return new Element(
        "p",
        Map.of(),
        List.of(
            new Element("t", Map.of("v", words), List.of(), ""),
            new Element(
                "footnoteRef",
                Map.of("href", "#" + eId, "marker", eId.substring(5)),
                List.of(),
                "")),
        "");
  }

  static Element occurrence(String eId, Element... blocks) {
    return new Element("occurrence", Map.of("eId", eId, "kind", "article"), List.of(blocks), "");
  }

  static Element table(Element... rows) {
    return new Element("table", Map.of(), List.of(rows), "");
  }

  static Element row(String... cells) {
    var tds = new ArrayList<Element>();
    for (var cell : cells) {
      tds.add(new Element("td", Map.of(), List.of(block("p", cell)), ""));
    }
    return new Element("tr", Map.of(), tds, "");
  }

  /** A block that says "The rate is x + {@code n}", with x + n a formula. */
  static Element formula(String n) {
    var math =
        new Element(
            "math",
            Map.of(),
            List.of(
                new Element("mi", Map.of(), List.of(), "x"),
                new Element("mo", Map.of(), List.of(), "+"),
                new Element("mn", Map.of(), List.of(), n)),
            "");
    return new Element(
        "p",
        Map.of(),
        List.of(new Element("t", Map.of("v", "The rate is "), List.of(), ""), math),
        "");
  }

  static Difference find(List<Difference> differences, String eId) {
    return differences.stream().filter(d -> d.eId().equals(eId)).findFirst().orElseThrow();
  }

  /** The one child of {@code occurrence} with the name {@code name}. */
  static Element only(Element occurrence, String name) {
    return occurrence.children().stream()
        .filter(child -> child.name().equals(name))
        .reduce(
            (one, other) -> {
              throw new AssertionError(
                  "More than one " + name + " in " + occurrence.attribute("eId"));
            })
        .orElseThrow();
  }

  /** What {@code element} says, with its edits written out as [+inserted] and [-deleted]. */
  static String said(Element element) {
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

  static List<Element> descendants(Element element) {
    return element.children().stream()
        .flatMap(child -> Stream.concat(Stream.of(child), descendants(child).stream()))
        .toList();
  }

  static Element text(String celex) {
    return Decoder.element(Encoder.encode(LegalTexts.sample("06.02.1/" + celex + ".xml")));
  }

  /** The occurrences of {@code earlier} and {@code later}, compared, each with those in it. */
  static List<ComparedOccurrence> compared(Element earlier, Element later) {
    var before = Occurrences.of(earlier);
    var after = Occurrences.of(later);
    var all = new ArrayList<ComparedOccurrence>();
    add(new Comparer(before, after, Pairing.of(before, after)).occurrences(null, null), all);
    return all;
  }

  private static void add(List<ComparedOccurrence> occurrences, List<ComparedOccurrence> all) {
    for (var each : occurrences) {
      all.add(each);
      add(each.occurrences(), all);
    }
  }

  /** The compared occurrence {@code eId}, of only the earlier text if {@code earlier}. */
  static ComparedOccurrence find(List<ComparedOccurrence> compared, String eId, boolean earlier) {
    return compared.stream()
        .filter(each -> each.occurrence().eId().equals(eId) && each.earlier() == earlier)
        .findFirst()
        .orElseThrow();
  }
}
