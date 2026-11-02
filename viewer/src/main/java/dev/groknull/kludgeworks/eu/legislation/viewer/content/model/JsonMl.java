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
 * A block's XML as JSON, as JsonML has it (jsonml.org): {@code [name, {attributes}, text or child
 * element…]}, names as legal-text gives them (LegalText). The plain mapping, both ways: what is
 * taken out of a block before it is hashed is Annotations' and NoteLinks' work, on this JSON.
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

  /** The element at {@code path} (positions among child elements) below {@code el}. */
  static List<?> at(List<?> el, List<?> path) {
    var at = el;
    for (var i : path) {
      at = elements(at).get(((Number) i).intValue());
    }
    return at;
  }

  /** A legal text's document: its element {@code el}, declaring the namespaces it uses. */
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

  /** {@code el} as an Element, and its children. */
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

  /** On a document's element: legal-text's namespace the default, the others by their prefixes. */
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
