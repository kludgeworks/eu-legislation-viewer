// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
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
          "79d6a7da47eb20a4d7322117e42822857f7ab3e00397bcfad4bfd33b53ad34bd",
          "46bcc9f6045daaabc662a7fc3ee997dd08c1944b55c697bf690fcb21d87183f4",
          "aabe5fe294bd7b014fa09d4fdeedb43e13f59dbf36361aeadb6aa16c9dc9abec",
          "5884feae0ef87c61a49c387934f6bcdb515071f20fccd028d1f47333c1a052f2",
          "3b80dfd486de5222b106d339d0fc5f21d022aeb9505769562e0c5d011e094d40",
          "6da55b7ded28d2265397f6ed22482675fc1d083d44934053da0d94ef46648898",
          "7334394aac7ca411c1c4a8525ac5c9cc5a199ec1ba7399c7349743236c88bfc4",
          "f3463c0aab19d00b853f9e85dbbfeb6d30dcd648bf3b1cadbe185b68b78fab05",
          "afba482a26ea11d2820117a81eb9eec4ab066a60ae21da7d1439a7f3085036b7",
          "a88508062e58a70e49f3c24de45d858f3b21816e9f0b109e73a193a7aea1c672",
          "8ab2158aa81c457f126637b6adf1f45451f8cc9f0ee90c902961878be57ee005",
          "46a43befc4317642c9c0fab10190b993a5392f11c8e9ed67e85910e96780087d");

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
   * Each difference shows both texts. Read without what it shows deleted, it says what the later
   * text says. Read without what it shows inserted, it says what the earlier text says. Thus no
   * word of either text is lost, and no word that only one text has shows unedited.
   *
   * <p>An occurrence is read by its own blocks. A difference that shows an occurrence whole is read
   * occurrence by occurrence, because a deleted occurrence shows beside an occurrence of the later
   * text, not always where it stood. The footnotes under an occurrence are not read.
   */
  @Test
  void showsWhatBothTextsSay() {
    var wrong = new ArrayList<String>();
    for (var pair : PAIRS) {
      var from = text(pair.get(0));
      var to = text(pair.get(1));
      var before = Occurrences.of(from);
      var after = Occurrences.of(to);
      var pairs = Pairing.of(before, after);
      var was = new HashMap<String, Occurrence>();
      for (var occurrence : after.byEId().values()) {
        Optional.ofNullable(pairs.was(occurrence))
            .ifPresent(earlier -> was.put(occurrence.eId(), earlier));
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
        if (!read(difference.shown(), Edits.DELETED).equals(later)) {
          wrong.add(
              footnote(pair, difference, "later", read(difference.shown(), Edits.DELETED), later));
        }
        if (!read(difference.shown(), Edits.INSERTED).equals(earlier)) {
          wrong.add(
              footnote(
                  pair, difference, "earlier", read(difference.shown(), Edits.INSERTED), earlier));
        }
      }
    }
    assertThat(wrong).isEmpty();
  }

  /** Returns where {@code difference}, read as one text, first says other than that text. */
  private static String footnote(
      List<String> pair,
      Difference difference,
      String text,
      Map<String, String> read,
      Map<String, String> said) {
    var occurrences = new TreeSet<>(read.keySet());
    occurrences.addAll(said.keySet());
    var occurrence =
        occurrences.stream()
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
            occurrence,
            read.getOrDefault(occurrence, ""),
            said.getOrDefault(occurrence, ""));
  }

  /**
   * Adds to {@code said} what each occurrence that {@code difference} shows says in {@code
   * occurrences}, by its eId: the occurrence of the difference, and the occurrences that it shows
   * in it.
   */
  private static void said(
      Occurrences occurrences, Difference difference, Map<String, String> said) {
    for (var eId : shown(difference)) {
      Optional.ofNullable(occurrences.get(eId)).ifPresent(occurrence -> put(said, eId, occurrence));
    }
  }

  /**
   * Adds to {@code said} what each occurrence that the changed {@code difference} shows said in the
   * earlier text, by the eId under which the difference shows it. An occurrence of both texts shows
   * under its later eId, and a deleted occurrence under its own.
   */
  private static void saidBefore(
      Occurrences before,
      Map<String, Occurrence> was,
      Difference difference,
      Map<String, String> said) {
    for (var eId : shown(difference)) {
      var paired = Optional.ofNullable(was.get(eId));
      paired
          .or(() -> deleted(difference.shown(), eId, before))
          .ifPresent(occurrence -> put(said, eId, occurrence));
    }
  }

  private static void put(Map<String, String> said, String eId, Occurrence occurrence) {
    var words = key(occurrence.blocks());
    if (!words.isEmpty()) {
      said.put(eId, words);
    }
  }

  /**
   * Returns the earlier occurrence {@code eId}, if {@code shown} shows every word of it deleted.
   */
  private static Optional<Occurrence> deleted(Element shown, String eId, Occurrences before) {
    var later =
        find(shown, eId).map(occurrence -> read(occurrence, Edits.DELETED)).orElseGet(Map::of);
    return later.containsKey(eId) ? Optional.empty() : Optional.ofNullable(before.get(eId));
  }

  private static Optional<Element> find(Element element, String eId) {
    if (eId.equals(Occurrences.eId(element))) {
      return Optional.of(element);
    }
    return element.children().stream().flatMap(child -> find(child, eId).stream()).findFirst();
  }

  /** Returns the eIds of the occurrences that {@code difference} shows. A table is one block. */
  private static List<String> shown(Difference difference) {
    var eIds = new ArrayList<String>();
    eIds(difference.shown(), eIds);
    return eIds;
  }

  private static void eIds(Element element, List<String> eIds) {
    var eId = Occurrences.eId(element);
    if (eId != null) {
      eIds.add(eId);
    }
    if (!Occurrences.block(element)) {
      element.children().forEach(child -> eIds(child, eIds));
    }
  }

  /**
   * Returns what each occurrence in {@code shown} says in its own blocks, by its eId, read without
   * what is in the edit element {@code without}. An occurrence that then says nothing is left out.
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
      Element element, String without, String occurrence, Map<String, StringBuilder> said) {
    var name = element.name();
    if (name.equals(without) || name.equals("footnotes") || Wording.SILENT.contains(name)) {
      return;
    }
    var eId = Occurrences.eId(element);
    var in = eId == null ? occurrence : eId;
    var words = said.computeIfAbsent(in, each -> new StringBuilder());
    if (Occurrences.block(element)) {
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

  /** Returns {@code element} without what is in the edit element {@code without}. */
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
