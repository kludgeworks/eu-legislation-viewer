// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The identifiers kept out of a node's content: eIds, which change when a text is renumbered while
 * what it says doesn't. Each is taken out as a fact, {@code ["eId", path, eId]}, the path its
 * element's place within a block ([] the node itself), and put back when the text is rebuilt. The
 * facts are the overlay on the edge to the node (Encoder): so the content is shared across
 * versions, and a renumbering changes only overlays, and the hashes of the containers above them.
 */
final class Annotations {
  static final String EID = "eId";

  private Annotations() {}

  /** Takes the eIds out of a block's JSON. */
  static List<Object> lift(List<?> leaf) {
    var facts = new ArrayList<Object>();
    lift(leaf, List.of(), facts);
    return facts;
  }

  /** Takes the eId out of a container's attributes. */
  static List<Object> lift(Map<String, Object> attributes) {
    var eId = attributes.remove(EID);
    return eId == null ? new ArrayList<>() : new ArrayList<>(List.of(List.of(EID, List.of(), eId)));
  }

  private static void lift(List<?> el, List<Integer> path, List<Object> facts) {
    var eId = JsonMl.attributes(el).remove(EID);
    if (eId != null) {
      facts.add(List.of(EID, path, eId));
    }
    var children = JsonMl.elements(el);
    for (var i = 0; i < children.size(); i++) {
      var child = new ArrayList<>(path);
      child.add(i);
      lift(children.get(i), List.copyOf(child), facts);
    }
  }

  /** Puts the eIds back into a block's JSON. */
  static void apply(List<?> leaf, List<?> facts) {
    for (var fact : facts) {
      var f = (List<?>) fact;
      if (f.getFirst().equals(EID)) {
        JsonMl.attributes(JsonMl.at(leaf, (List<?>) f.get(1))).put(EID, f.get(2));
      }
    }
  }

  /** Puts the eId back into a container's attributes. */
  static void apply(Map<String, Object> attributes, List<?> facts) {
    for (var fact : facts) {
      var f = (List<?>) fact;
      if (f.getFirst().equals(EID)) {
        attributes.put(EID, f.get(2));
      }
    }
  }
}
