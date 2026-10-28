// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

/**
 * One Formex document as Cellar serves it: the files of one folder, fixtures/formex/&lt;Formex
 * version&gt;/&lt;CELEX&gt;/ or fixtures/refused/&lt;root element&gt;/.
 *
 * @param name the folder relative to fixtures/, e.g. formex/06.02.1/02015R1998-20260322
 * @param folder the folder
 */
record Fixture(String name, Path folder) {

  private static final Pattern ROOT_ELEMENT = Pattern.compile("<([A-Z][A-Z0-9.]*)[\\s>/]");
  private static final Pattern OJ_CELEX = Pattern.compile("[1-9E]\\d{4}[A-Z]{1,2}[0-9A-Z()]+");

  /**
   * The file to convert: a consolidation's CONS.ACT or CONS.OTH file, else the package descriptor
   * (DOC), else the largest file.
   */
  Path input() {
    var files = xmlFiles();
    return first(files, root -> root.startsWith("CONS."))
        .or(() -> first(files, "DOC"::equals))
        .orElseGet(() -> files.stream().max(Comparator.comparingLong(Fixture::size)).orElseThrow());
  }

  /** The input with its system id set, so the files it refers to resolve. */
  Source source() {
    return new StreamSource(input().toFile());
  }

  /**
   * The CELEX number to pass for an OJ act, which the folder is named after; consolidations (CELEX
   * 0…) carry their own, and refused documents have none.
   */
  Optional<String> celex() {
    var folderName = folder.getFileName().toString();
    return OJ_CELEX.matcher(folderName).matches() ? Optional.of(folderName) : Optional.empty();
  }

  @Override
  public String toString() {
    return name;
  }

  private List<Path> xmlFiles() {
    try (var files = Files.list(folder)) {
      return files.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Optional<Path> first(List<Path> files, Predicate<String> root) {
    return files.stream().filter(f -> root.test(rootElement(f))).findFirst();
  }

  /** The root element's name, read from the start of the file. */
  private static String rootElement(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      var matcher = ROOT_ELEMENT.matcher(new String(in.readNBytes(4096), StandardCharsets.UTF_8));
      return matcher.find() ? matcher.group(1) : "";
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static long size(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
