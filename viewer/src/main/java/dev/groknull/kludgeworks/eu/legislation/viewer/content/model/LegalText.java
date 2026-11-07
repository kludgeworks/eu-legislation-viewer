// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * What the content model knows of the legal-text schema.
 *
 * <ul>
 *   <li>Its namespaces, and the names they give to elements and attributes in JSON. A name is
 *       local, or prefixed, for example {@code m:math} or {@code xml:lang}.
 *   <li>Which elements are blocks. Blocks are the leaves of the tree.
 *   <li>That text is never mixed with elements. Text is only in an element of its own, {@code t}.
 *       Thus whitespace between elements is only the indentation of the writer of the file.
 * </ul>
 */
final class LegalText {

  static final String NAMESPACE = "https://kludgeworks.groknull.dev/ns/legal-text/1";

  /**
   * The other namespaces that a legal text uses, by the prefix that their names have in JSON. For
   * example, {@code m} in {@code m:math}.
   */
  static final Map<String, String> NAMESPACES =
      Map.of(
          "m", "http://www.w3.org/1998/Math/MathML",
          "xml", XMLConstants.XML_NS_URI,
          "xsi", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);

  /**
   * The blocks. A block is what a reader reads and what an amendment replaces. Any element that
   * holds only text is also a block.
   */
  private static final Set<String> BLOCKS =
      Set.of("p", "heading", "tr", "note", "longTitle", "num", "docTitle");

  private LegalText() {}

  static boolean isLeaf(XdmNode el) {
    return BLOCKS.contains(elementName(el.getNodeName())) || !hasElements(el);
  }

  static boolean isRoot(XdmNode el) {
    return new QName(NAMESPACE, "legalText").equals(el.getNodeName());
  }

  /**
   * Returns the child elements of an element. It refuses anything else except whitespace, for
   * example text beside elements, comments or processing instructions.
   */
  static List<XdmNode> elements(XdmNode el) {
    var out = new ArrayList<XdmNode>();
    for (var child : el.children()) {
      switch (child.getNodeKind()) {
        case ELEMENT -> out.add(child);
        case TEXT -> {
          if (!child.getStringValue().isBlank()) {
            throw new IllegalArgumentException(
                "Text beside elements in "
                    + elementName(el.getNodeName())
                    + ": "
                    + child.getStringValue().strip());
          }
        }
        default ->
            throw new IllegalArgumentException(
                "A " + child.getNodeKind() + " in " + elementName(el.getNodeName()));
      }
    }
    return out;
  }

  static boolean hasElements(XdmNode el) {
    for (var child : el.children()) {
      if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the largest number of rows that a cell of a table row spans. {@code leaf} is the JSON
   * of a leaf. Returns 0 for anything that is not a row.
   */
  static int rowspan(List<?> leaf) {
    if (!JsonMl.name(leaf).equals("tr")) {
      return 0;
    }
    var most = 1;
    for (var cell : JsonMl.elements(leaf)) {
      if (JsonMl.attributes(cell).get("rowspan") instanceof String span) {
        most = Math.max(most, Integer.parseInt(span.strip()));
      }
    }
    return most;
  }

  static String elementName(QName name) {
    var uri = name.getNamespace();
    return uri.equals(NAMESPACE) ? name.getLocalName() : prefix(uri) + ":" + name.getLocalName();
  }

  static String attributeName(QName name) {
    var uri = name.getNamespace();
    return uri.isEmpty() ? name.getLocalName() : prefix(uri) + ":" + name.getLocalName();
  }

  private static String prefix(String uri) {
    return NAMESPACES.entrySet().stream()
        .filter(e -> e.getValue().equals(uri))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("A namespace legal-text doesn't use: " + uri));
  }
}
