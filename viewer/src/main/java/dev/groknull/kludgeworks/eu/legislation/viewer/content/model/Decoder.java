// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.HashMap;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

/**
 * Reads a legal text back from the JSON that {@link Encoder} writes. It parses the JSON as a stream
 * of tokens, straight into {@link Element} records, without a tree of maps and lists between.
 */
public final class Decoder {
  private static final JsonFactory JSON = new JsonFactory();

  private final JsonParser json;

  private Decoder(JsonParser json) {
    this.json = json;
  }

  /** Returns the element of the text, {@code legalText}, with everything in it. */
  public static Element element(byte[] encoded) {
    try (var json = JSON.createParser(ObjectReadContext.empty(), encoded)) {
      var decoder = new Decoder(json);
      decoder.expect(json.nextToken(), JsonToken.START_ARRAY);
      var element = decoder.element();
      if (json.nextToken() != null) {
        throw new IllegalArgumentException("More JSON after the text");
      }
      return element;
    }
  }

  /** Reads the element whose array has just started, up to the end of its array. */
  private Element element() {
    expect(json.nextToken(), JsonToken.VALUE_STRING);
    var name = json.getString();
    expect(json.nextToken(), JsonToken.START_OBJECT);
    var attributes = new HashMap<String, String>();
    while (json.nextToken() == JsonToken.PROPERTY_NAME) {
      var attribute = json.currentName();
      expect(json.nextToken(), JsonToken.VALUE_STRING);
      attributes.put(attribute, json.getString());
    }
    var children = new ArrayList<Element>();
    var text = "";
    for (var token = json.nextToken(); token != JsonToken.END_ARRAY; token = json.nextToken()) {
      switch (token) {
        case START_ARRAY -> children.add(element());
        case VALUE_STRING -> text = json.getString();
        case null, default -> throw unexpected(token);
      }
    }
    return new Element(name, attributes, children, text);
  }

  private void expect(JsonToken token, JsonToken expected) {
    if (token != expected) {
      throw unexpected(token);
    }
  }

  private IllegalArgumentException unexpected(JsonToken token) {
    return new IllegalArgumentException(
        "Not a legal text's JSON: " + token + " at " + json.currentLocation());
  }
}
