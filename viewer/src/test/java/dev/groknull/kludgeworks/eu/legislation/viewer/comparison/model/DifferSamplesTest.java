// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Units.Unit;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Checks what {@link Differ} finds between the versions of the acts among the converter's samples
 * that have more than one.
 *
 * <ul>
 *   <li>Each comparison is pinned. It is written out whole, records by their components and maps by
 *       their keys in order, and pinned by its SHA-256. A change that only makes the comparison
 *       faster must keep every one.
 *   <li>Each difference shows what both texts say.
 * </ul>
 */
class DifferSamplesTest {

  /** The pairs compared, earlier then later, by their paths below {@code expected/formex}. */
  private static final List<List<String>> PAIRS =
      List.of(
          List.of("05.10/02006D0944-20101216.xml", "05.51/02006D0944-20131112.xml"),
          List.of("06.02.1/02017G0119-20220128.xml", "06.02.1/02017G0119-20260429.xml"),
          List.of("06.02.1/02018R2019-20250220.xml", "06.02.1/02018R2019-20250623.xml"),
          List.of("06.02.1/02022D2319-20241216.xml", "06.02.1/02022D2319-20250326.xml"),
          List.of("05.55/32015R1998.xml", "05.51/02015R1998-20160229.xml"),
          List.of("05.51/02015R1998-20160229.xml", "05.56/02015R1998-20200216.xml"),
          List.of("05.56/02015R1998-20200216.xml", "05.59/02015R1998-20211231.xml"),
          List.of("05.59/02015R1998-20211231.xml", "06.02.1/02015R1998-20260101.xml"),
          List.of("06.02.1/02015R1998-20260101.xml", "06.02.1/02015R1998-20260322.xml"),
          List.of("06.02.1/02015R1998-20260322.xml", "06.02.1/02015R1998-20261001.xml"),
          List.of("05.55/32015R1998.xml", "06.02.1/02015R1998-20261001.xml"),
          List.of("06.02.1/02015R1998-20261001.xml", "06.02.1/02015R1998-20260101.xml"));

  /** The SHA-256 of each comparison of {@link #PAIRS} written out, in order. */
  private static final List<String> FOUND =
      List.of(
          "dcccb4670928e8ef7cc07037f29d7229767c12365af4459bfc430dbd41c8e3d6",
          "8426408e54674fe4a15aaddd2eb2c89dc3710f632d5360ac3f74ffa04f64d688",
          "27dc5fce68f549247440e2095000a4c28d8ffdc2c46a33bf4c043a80e427610e",
          "e0861247c0e9c41d470c4e5566d4009ef44e2181927816d9e7358cfcc8e85842",
          "05ed3eb163fedbcb7ada6a99922eeb609ce62cac57a69649fd0882f88eddf4e8",
          "ef57c2ced8b8eb5006d3621110aae221168df4791b6b02be091d4ed936d5a54a",
          "c74da5de23b9489629c93bf52519bcfb94eb9a4950bc25c644b5a6dfbfbcffa2",
          "a2e9736be75c16d79ccd9e4893cf12ecbd3c7aa98d4d85b9ee0655ba43248324",
          "291fa589d51eb9060bd482c731c99edb8b34f57ae6c20ef1d82b000717a2d0b2",
          "6eb675b8f7f06301cd67aab2f2dcec53cad3f08240f075b9207955ee4464b8fd",
          "eee0075485a6cc30b0361b47d7461b6ab8e8b2a0be63a1ca80506869bb2a69fc",
          "b0a638230f4db68a50826410e1dade307fecaafdb7079cbc9c64a05f89d5b1c8");

  @Test
  void findsWhatItFoundBefore() {
    var found = new ArrayList<String>();
    for (var pair : PAIRS) {
      var compared = Differ.differences(text(pair.get(0)), text(pair.get(1)));
      found.add(sha256(written(compared)));
    }
    assertThat(found).isEqualTo(FOUND);
  }

  /**
   * Each difference shows both texts. Read without what it marks deleted, it says what the later
   * text says. Read without what it marks inserted, it says what the earlier text says. Thus no
   * word of either text is lost, and no word that only one text has shows unmarked.
   *
   * <p>A unit is read by its own blocks. A difference that shows a unit whole is read unit by unit,
   * because a deleted unit shows beside a unit of the later text, not always where it stood. The
   * notes under a unit are not read.
   */
  @Test
  void showsWhatBothTextsSay() {
    var wrong = new ArrayList<String>();
    for (var pair : PAIRS) {
      var from = text(pair.get(0));
      var to = text(pair.get(1));
      var before = Units.of(from);
      var after = Units.of(to);
      var pairs = Pairing.of(before, after);
      var was = new HashMap<String, Unit>();
      for (var unit : after.byEId().values()) {
        Optional.ofNullable(pairs.was(unit)).ifPresent(earlier -> was.put(unit.eId(), earlier));
      }
      for (var difference : Differ.differences(from, to).differences()) {
        var later = new TreeMap<String, String>();
        var earlier = new TreeMap<String, String>();
        if (difference.kind() != Kind.DELETED) {
          said(after, difference, later);
        }
        if (difference.kind() == Kind.DELETED) {
          said(before, difference, earlier);
        } else if (difference.kind() == Kind.CHANGED) {
          saidBefore(before, was, difference, earlier);
        }
        if (!read(difference.shown(), Marks.DELETED).equals(later)) {
          wrong.add(
              note(pair, difference, "later", read(difference.shown(), Marks.DELETED), later));
        }
        if (!read(difference.shown(), Marks.INSERTED).equals(earlier)) {
          wrong.add(
              note(pair, difference, "earlier", read(difference.shown(), Marks.INSERTED), earlier));
        }
      }
    }
    assertThat(wrong).isEmpty();
  }

  /** Returns where {@code difference}, read as one text, first says other than that text. */
  private static String note(
      List<String> pair,
      Difference difference,
      String text,
      Map<String, String> read,
      Map<String, String> said) {
    var units = new TreeSet<>(read.keySet());
    units.addAll(said.keySet());
    var unit =
        units.stream()
            .filter(eId -> !read.getOrDefault(eId, "").equals(said.getOrDefault(eId, "")))
            .findFirst()
            .orElse("");
    return "%s -> %s, %s %s, read as the %s text, in %s: shows \"%s\" for \"%s\""
        .formatted(
            pair.get(0),
            pair.get(1),
            difference.kind(),
            difference.eId(),
            text,
            unit,
            read.getOrDefault(unit, ""),
            said.getOrDefault(unit, ""));
  }

  /**
   * Adds to {@code said} what each unit that {@code difference} shows says in {@code units}, by its
   * eId: the unit of the difference, and the units that it shows in it.
   */
  private static void said(Units units, Difference difference, Map<String, String> said) {
    for (var eId : shown(difference)) {
      Optional.ofNullable(units.get(eId)).ifPresent(unit -> put(said, eId, unit));
    }
  }

  /**
   * Adds to {@code said} what each unit that the changed {@code difference} shows said in the
   * earlier text, by the eId under which the difference shows it. A unit of both texts shows under
   * its later eId, and a deleted unit under its own.
   */
  private static void saidBefore(
      Units before, Map<String, Unit> was, Difference difference, Map<String, String> said) {
    for (var eId : shown(difference)) {
      var paired = Optional.ofNullable(was.get(eId));
      paired
          .or(() -> deleted(difference.shown(), eId, before))
          .ifPresent(unit -> put(said, eId, unit));
    }
  }

  private static void put(Map<String, String> said, String eId, Unit unit) {
    var words = key(unit.blocks());
    if (!words.isEmpty()) {
      said.put(eId, words);
    }
  }

  /** Returns the earlier unit {@code eId}, if {@code shown} marks every word of it deleted. */
  private static Optional<Unit> deleted(Element shown, String eId, Units before) {
    var later = find(shown, eId).map(unit -> read(unit, Marks.DELETED)).orElseGet(Map::of);
    return later.containsKey(eId) ? Optional.empty() : Optional.ofNullable(before.get(eId));
  }

  private static Optional<Element> find(Element element, String eId) {
    if (eId.equals(Units.eId(element))) {
      return Optional.of(element);
    }
    return element.children().stream().flatMap(child -> find(child, eId).stream()).findFirst();
  }

  /** Returns the eIds of the units that {@code difference} shows. A table is one block. */
  private static List<String> shown(Difference difference) {
    var eIds = new ArrayList<String>();
    eIds(difference.shown(), eIds);
    return eIds;
  }

  private static void eIds(Element element, List<String> eIds) {
    var eId = Units.eId(element);
    if (eId != null) {
      eIds.add(eId);
    }
    if (!Units.block(element)) {
      element.children().forEach(child -> eIds(child, eIds));
    }
  }

  /**
   * Returns what each unit in {@code shown} says in its own blocks, by its eId, read without what
   * is in the mark {@code without}. A unit that then says nothing is left out.
   */
  private static Map<String, String> read(Element shown, String without) {
    var said = new TreeMap<String, StringBuilder>();
    read(shown, without, "", said);
    var read = new TreeMap<String, String>();
    said.forEach(
        (eId, words) -> {
          if (!words.isEmpty()) {
            read.put(eId, words.toString());
          }
        });
    return read;
  }

  private static void read(
      Element element, String without, String unit, Map<String, StringBuilder> said) {
    var name = element.name();
    if (name.equals(without) || name.equals("notes") || Wording.MARKS.contains(name)) {
      return;
    }
    var eId = Units.eId(element);
    var in = eId == null ? unit : eId;
    var words = said.computeIfAbsent(in, each -> new StringBuilder());
    if (Units.block(element)) {
      words.append(Wording.key(without(element, without)));
      return;
    }
    // Wording reads a quote as its opening mark, what is in it, and its closing mark.
    var quote = name.equals("quote");
    words.append(quote ? mark(element, "startQuote") : Wording.key(leaf(element)));
    element.children().forEach(child -> read(child, without, in, said));
    if (quote) {
      words.append(mark(element, "endQuote"));
    }
  }

  private static String mark(Element quote, String attribute) {
    return Wording.key(
        new Element(
            "t", Map.of("v", quote.attributes().getOrDefault(attribute, "")), List.of(), ""));
  }

  /** Returns {@code element} without its children: only what it says itself. */
  private static Element leaf(Element element) {
    return new Element(element.name(), element.attributes(), List.of(), element.text());
  }

  /** Returns {@code element} without what is in the mark {@code without}. */
  private static Element without(Element element, String without) {
    var children =
        element.children().stream()
            .filter(child -> !child.name().equals(without))
            .map(child -> without(child, without))
            .toList();
    return new Element(element.name(), element.attributes(), children, element.text());
  }

  private static String key(List<Element> blocks) {
    var said = new StringBuilder();
    blocks.forEach(block -> said.append(Wording.key(block)));
    return said.toString();
  }

  private static Element text(String path) {
    return Decoder.element(Encoder.encode(LegalTexts.sample(path)));
  }

  /** {@code value} written out, the same on every run: a map by its keys, in order. */
  private static String written(Object value) {
    var out = new StringBuilder();
    write(value, out);
    return out.toString();
  }

  private static void write(Object value, StringBuilder out) {
    switch (value) {
      case null -> out.append("null");
      case Record record -> {
        out.append(record.getClass().getSimpleName()).append('(');
        for (var component : record.getClass().getRecordComponents()) {
          out.append(component.getName()).append('=');
          try {
            write(component.getAccessor().invoke(record), out);
          } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
          }
          out.append(';');
        }
        out.append(')');
      }
      case List<?> list -> {
        out.append('[');
        list.forEach(
            each -> {
              write(each, out);
              out.append(',');
            });
        out.append(']');
      }
      case Map<?, ?> map -> {
        out.append('{');
        var sorted = new TreeMap<String, Object>();
        map.forEach((key, each) -> sorted.put(String.valueOf(key), each));
        sorted.forEach(
            (key, each) -> {
              out.append(key).append('=');
              write(each, out);
              out.append(',');
            });
        out.append('}');
      }
      default -> out.append(value);
    }
  }

  private static String sha256(String written) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(written.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
