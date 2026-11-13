// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import java.io.ByteArrayOutputStream;
import java.util.TreeMap;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * Writes a legal text as JSON, in the JsonML form (jsonml.org): {@code [name, {attributes}, child
 * element…]}, or {@code [name, {attributes}, text]} for an element of text. The names are as {@link
 * LegalText} gives them. {@link Decoder} reads it back.
 *
 * <p>The same text always gives the same bytes: the attributes are written sorted by name, and the
 * whitespace between elements is left out. Thus the SHA-256 of the JSON tells whether a text
 * changed.
 */
public final class Encoder {
  private static final JsonFactory JSON = new JsonFactory();

  private Encoder() {}

  /**
   * @param legalText a legal-text document, or its element
   */
  public static byte[] encode(XdmNode legalText) {
    var root = legalText;
    if (root.getNodeKind() == XdmNodeKind.DOCUMENT) {
      root = LegalText.elements(root).getFirst();
    }
    if (!LegalText.isRoot(root)) {
      throw new IllegalArgumentException("Not legal-text: " + root.getNodeName().getClarkName());
    }
    var out = new ByteArrayOutputStream();
    try (var json = JSON.createGenerator(ObjectWriteContext.empty(), out)) {
      write(root, json);
    }
    return out.toByteArray();
  }

  private static void write(XdmNode el, JsonGenerator json) {
    json.writeStartArray();
    json.writeString(LegalText.elementName(el.getNodeName()));
    var attributes = new TreeMap<String, String>();
    el.axisIterator(Axis.ATTRIBUTE)
        .forEachRemaining(
            a -> attributes.put(LegalText.attributeName(a.getNodeName()), a.getStringValue()));
    json.writeStartObject();
    for (var attribute : attributes.entrySet()) {
      json.writeName(attribute.getKey());
      json.writeString(attribute.getValue());
    }
    json.writeEndObject();
    if (LegalText.hasElements(el)) {
      for (var child : LegalText.elements(el)) {
        write(child, json);
      }
    } else {
      var text = new StringBuilder();
      for (var child : el.children()) {
        if (child.getNodeKind() != XdmNodeKind.TEXT) {
          throw new IllegalArgumentException(
              "A " + child.getNodeKind() + " in " + LegalText.elementName(el.getNodeName()));
        }
        text.append(child.getStringValue());
      }
      if (!text.isEmpty()) {
        json.writeString(text.toString());
      }
    }
    json.writeEndArray();
  }
}
