// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * Gives legal texts for the tests. The texts are the converter's expected output for its fixtures,
 * parsed. There are 229 documents, in every Formex version the converter handles. Some have MathML.
 *
 * <p>This class also gives a text's element as the converter wrote it, and the canonical form of a
 * text, to compare a decoded text with the original. The canonical form has the elements,
 * attributes and text. It does not have the whitespace between elements, which comes from the
 * indentation of the files.
 */
@AutoBazelRepository
public final class LegalTexts {
  public static final Processor SAXON = new Processor(false);

  private LegalTexts() {}

  /**
   * The expected outputs, by their path below {@code expected/formex}. An example path is {@code
   * 06.02.1/02015R1998-20260322.xml}.
   */
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

  /**
   * The element of a text, as the converter wrote it: read straight from the parsed XML, not
   * through the JSON that the content module stores.
   */
  public static Element element(XdmNode text) {
    var root = text;
    if (root.getNodeKind() == XdmNodeKind.DOCUMENT) {
      root = LegalText.elements(root).getFirst();
    }
    return of(root);
  }

  private static Element of(XdmNode el) {
    var attributes = new HashMap<String, String>();
    el.axisIterator(Axis.ATTRIBUTE)
        .forEachRemaining(
            a -> attributes.put(LegalText.attributeName(a.getNodeName()), a.getStringValue()));
    var children = new ArrayList<Element>();
    var text = "";
    if (LegalText.hasElements(el)) {
      LegalText.elements(el).forEach(child -> children.add(of(child)));
    } else {
      text = el.getStringValue();
    }
    return new Element(LegalText.elementName(el.getNodeName()), attributes, children, text);
  }

  /**
   * The elements, attributes and text of an element, one per line, in the form of {@link
   * #canonical(XdmNode)}.
   */
  public static String canonical(Element element) {
    var out = new StringBuilder();
    canonical(element, 0, out);
    return out.toString();
  }

  private static void canonical(Element element, int depth, StringBuilder out) {
    var attrs = new TreeMap<String, String>();
    element.attributes().forEach((name, value) -> attrs.put(clark(name, ""), value));
    out.append("  ".repeat(depth)).append(clark(element.name(), LegalText.NAMESPACE)).append(' ');
    out.append(attrs).append('\n');
    element.children().forEach(child -> canonical(child, depth + 1, out));
    if (!element.text().isEmpty()) {
      out.append("  ".repeat(depth + 1)).append('"').append(element.text()).append("\"\n");
    }
  }

  /** A name as JSON writes it, in Clark's form, with {@code namespace} if it has no prefix. */
  private static String clark(String name, String namespace) {
    var colon = name.indexOf(':');
    if (colon < 0) {
      return namespace.isEmpty() ? name : "{" + namespace + "}" + name;
    }
    return "{"
        + LegalText.NAMESPACES.get(name.substring(0, colon))
        + "}"
        + name.substring(colon + 1);
  }

  /**
   * The elements, attributes and text of a text, one per line. The whitespace between elements is
   * left out.
   */
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
