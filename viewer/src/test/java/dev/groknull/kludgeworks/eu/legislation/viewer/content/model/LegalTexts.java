// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * Legal texts for the tests: the converter's expected output for its fixtures (229 documents, in
 * every Formex version it handles, some with MathML), parsed; and what a text is, to compare one
 * rebuilt with the original: its elements, attributes and text, without the whitespace between
 * elements (written as the files are indented).
 */
@AutoBazelRepository
public final class LegalTexts {
  public static final Processor SAXON = new Processor(false);

  private LegalTexts() {}

  /** The expected outputs, by their path below expected/formex: 06.02.1/02015R1998-20260322.xml. */
  public static Stream<String> samples() {
    var root = expected();
    try (var files = Files.walk(root)) {
      return files
          .filter(f -> f.toString().endsWith(".xml"))
          .map(f -> root.relativize(f).toString())
          .sorted()
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static XdmNode sample(String path) {
    return parse(expected().resolve(path));
  }

  public static XdmNode parse(Path file) {
    try {
      return SAXON.newDocumentBuilder().build(file.toFile());
    } catch (SaxonApiException e) {
      throw new IllegalArgumentException(file.toString(), e);
    }
  }

  public static XdmNode parse(String xml) {
    try {
      return SAXON.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
    } catch (SaxonApiException e) {
      throw new IllegalArgumentException(xml, e);
    }
  }

  /** A text rebuilt from its tree, as a document. */
  public static XdmNode rebuild(Tree tree) {
    try {
      var out = SAXON.newDocumentBuilder().newBuildingStreamWriter();
      Decoder.write(tree, out);
      return out.getDocumentNode();
    } catch (XMLStreamException | SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A text's elements, attributes and text, one per line, but no whitespace between elements. */
  public static String canonical(XdmNode node) {
    var out = new StringBuilder();
    canonical(node, 0, out);
    return out.toString();
  }

  private static void canonical(XdmNode node, int depth, StringBuilder out) {
    if (node.getNodeKind() == XdmNodeKind.DOCUMENT) {
      node.children().forEach(c -> canonical(c, depth, out));
      return;
    }
    switch (node.getNodeKind()) {
      case ELEMENT -> {
        var attrs = new TreeMap<String, String>();
        node.axisIterator(Axis.ATTRIBUTE)
            .forEachRemaining(a -> attrs.put(a.getNodeName().getClarkName(), a.getStringValue()));
        out.append("  ".repeat(depth)).append(node.getNodeName().getClarkName()).append(' ');
        out.append(attrs).append('\n');
        var elements = new ArrayList<XdmNode>();
        node.children().forEach(elements::add);
        var mixed = elements.stream().anyMatch(c -> c.getNodeKind() == XdmNodeKind.ELEMENT);
        for (var child : elements) {
          if (child.getNodeKind() == XdmNodeKind.TEXT
              && mixed
              && child.getStringValue().isBlank()) {
            continue;
          }
          canonical(child, depth + 1, out);
        }
      }
      case TEXT ->
          out.append("  ".repeat(depth)).append('"').append(node.getStringValue()).append("\"\n");
      default -> out.append("  ".repeat(depth)).append(node.getNodeKind()).append('\n');
    }
  }

  private static Path expected() {
    try {
      return Path.of(
          Runfiles.preload()
              .withSourceRepository(AutoBazelRepository_LegalTexts.NAME)
              .rlocation("kludgeworks_viewer/formex-converter/src/test/resources/expected/formex"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static List<String> lines(String canonical) {
    return canonical.lines().toList();
  }
}
