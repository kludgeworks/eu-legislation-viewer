// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * Maps the XML of a block to JSON, in the JsonML form (jsonml.org), and back. The form is {@code
 * [name, {attributes}, text or child element…]}. The names are as legal-text gives them, in {@link
 * LegalText}. This is only the plain mapping. {@link Annotations} and {@link NoteLinks} take things
 * out of a block on this JSON, before it is hashed.
 */
final class JsonMl {

  private JsonMl() {}

  static List<Object> of(XdmNode el) {
    var out =
        new ArrayList<Object>(List.of(LegalText.elementName(el.getNodeName()), attributes(el)));
    if (LegalText.hasElements(el)) {
      for (var child : LegalText.elements(el)) {
        out.add(of(child));
      }
    } else {
      for (var text : el.children()) {
        if (text.getNodeKind() != XdmNodeKind.TEXT) {
          throw new IllegalArgumentException("A " + text.getNodeKind() + " in " + out.getFirst());
        }
        out.add(text.getStringValue());
      }
    }
    return out;
  }

  static Map<String, Object> attributes(XdmNode el) {
    var out = new LinkedHashMap<String, Object>();
    el.axisIterator(Axis.ATTRIBUTE)
        .forEachRemaining(
            a -> out.put(LegalText.attributeName(a.getNodeName()), a.getStringValue()));
    return out;
  }

  static String name(List<?> el) {
    return (String) el.getFirst();
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> attributes(List<?> el) {
    return (Map<String, Object>) el.get(1);
  }

  static List<List<?>> elements(List<?> el) {
    var out = new ArrayList<List<?>>();
    for (var part : el.subList(2, el.size())) {
      if (part instanceof List<?> child) {
        out.add(child);
      }
    }
    return out;
  }

  /**
   * Returns the element at {@code path} below {@code el}. The path holds positions among child
   * elements.
   */
  static List<?> at(List<?> el, List<?> path) {
    var at = el;
    for (var i : path) {
      at = elements(at).get(((Number) i).intValue());
    }
    return at;
  }

  /**
   * Writes the document of a legal text, with its element {@code el}. The element declares the
   * namespaces that the text uses.
   */
  static void writeDocument(List<?> el, XMLStreamWriter out) throws XMLStreamException {
    out.writeStartDocument();
    write(el, out, true);
    out.writeEndDocument();
  }

  static void write(List<?> el, XMLStreamWriter out) throws XMLStreamException {
    write(el, out, false);
  }

  private static void write(List<?> el, XMLStreamWriter out, boolean document)
      throws XMLStreamException {
    startElement(name(el), out);
    if (document) {
      declareNamespaces(out);
    }
    for (var a : attributes(el).entrySet()) {
      attribute(a.getKey(), (String) a.getValue(), out);
    }
    for (var part : el.subList(2, el.size())) {
      if (part instanceof List<?> child) {
        write(child, out);
      } else {
        out.writeCharacters((String) part);
      }
    }
    out.writeEndElement();
  }

  static void startElement(String name, XMLStreamWriter out) throws XMLStreamException {
    var colon = name.indexOf(':');
    if (colon < 0) {
      out.writeStartElement("", name, LegalText.NAMESPACE);
    } else {
      var prefix = name.substring(0, colon);
      out.writeStartElement(prefix, name.substring(colon + 1), LegalText.NAMESPACES.get(prefix));
    }
  }

  /** Returns {@code el} and its children as an {@code Element}. */
  static Element element(List<?> el) {
    var attributes = new LinkedHashMap<String, String>();
    attributes(el).forEach((name, value) -> attributes.put(name, (String) value));
    var children = new ArrayList<Element>();
    var text = new StringBuilder();
    for (var part : el.subList(2, el.size())) {
      if (part instanceof List<?> child) {
        children.add(element(child));
      } else {
        text.append((String) part);
      }
    }
    return new Element(name(el), attributes, children, text.toString());
  }

  /**
   * Declares the namespaces on the element of a document. The legal-text namespace is the default.
   * The others are declared by their prefixes.
   */
  private static void declareNamespaces(XMLStreamWriter out) throws XMLStreamException {
    out.writeDefaultNamespace(LegalText.NAMESPACE);
    for (var ns : LegalText.NAMESPACES.entrySet()) {
      if (!ns.getKey().equals("xml")) {
        out.writeNamespace(ns.getKey(), ns.getValue());
      }
    }
  }

  static void attribute(String name, String value, XMLStreamWriter out) throws XMLStreamException {
    var colon = name.indexOf(':');
    if (colon < 0) {
      out.writeAttribute(name, value);
    } else {
      var prefix = name.substring(0, colon);
      out.writeAttribute(
          prefix, LegalText.NAMESPACES.get(prefix), name.substring(colon + 1), value);
    }
  }
}
