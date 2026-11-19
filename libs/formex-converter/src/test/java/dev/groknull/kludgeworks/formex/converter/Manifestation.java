// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
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
import java.util.zip.GZIPInputStream;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

/**
 * One Formex manifestation as Cellar serves it (Cellar's FRBR levels: work, expression,
 * manifestation, items): its items, the files of one folder. A fixture is one,
 * fixtures/formex/&lt;Formex version&gt;/&lt;CELEX&gt;/ or fixtures/refused/&lt;root element&gt;/;
 * so is each manifestation of the corpus mirror, files/&lt;Cellar ID&gt;/. The mirror keeps the
 * files as Cellar sends them, some consolidations' gzipped (made from 2005 to 2018): those are read
 * unzipped, as the viewer reads them (its ContentStreams). None of them refers to another XML file
 * of its package, so the input is all there is to unzip.
 *
 * @param name how tests name it: its folder relative to fixtures/
 *     (formex/06.02.1/02015R1998-20260322) or its Cellar ID
 * @param folder the folder
 */
record Manifestation(String name, Path folder) {

  private static final Pattern ROOT_ELEMENT = Pattern.compile("<([A-Z][A-Z0-9.]*)[\\s>/]");
  private static final Pattern CELEX =
      Pattern.compile("[1-9E]\\d{4}[A-Z]{1,2}[0-9A-Z()]+|0\\d{4}[A-Z]{1,2}[0-9A-Z()]+-\\d{8}");

  /**
   * The file to convert, one of the stylesheet's inputs: a consolidation's CONS.ACT or CONS.OTH
   * file, else the package descriptor (DOC), else the largest file. The viewer chooses the same way
   * (its FormexConverter.convert).
   */
  Path input() {
    var files = xmlFiles();
    return first(files, root -> root.startsWith("CONS."))
        .or(() -> first(files, "DOC"::equals))
        .orElseGet(
            () -> files.stream().max(Comparator.comparingLong(Manifestation::size)).orElseThrow());
  }

  /** The input with its system id set, so the files it refers to resolve; unzipped if gzipped. */
  Source source() {
    var input = input();
    return gzipped(input)
        ? new StreamSource(new ByteArrayInputStream(unzipped(input)), input.toUri().toString())
        : new StreamSource(input.toFile());
  }

  private static boolean gzipped(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      return in.read() == 0x1f && in.read() == 0x8b;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] unzipped(Path file) {
    try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@code file}'s bytes, unzipped if gzipped. */
  private static InputStream open(Path file) throws IOException {
    var in = new BufferedInputStream(Files.newInputStream(file));
    in.mark(2);
    var gzip = in.read() == 0x1f && in.read() == 0x8b;
    in.reset();
    return gzip ? new GZIPInputStream(in) : in;
  }

  /**
   * The CELEX number to pass, as ingest passes Cellar's, which the folder is named after: an OJ
   * act's or a consolidation's. A consolidated treaty's (02016E/TXT-20250315) can't name a folder,
   * so its Formex's is used; refused documents have none.
   */
  Optional<String> celex() {
    var folderName = folder.getFileName().toString();
    return CELEX.matcher(folderName).matches() ? Optional.of(folderName) : Optional.empty();
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
    try (InputStream in = open(file)) {
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
