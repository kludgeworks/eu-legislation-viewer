// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decides when two nodes are the same. Two nodes are the same if the SHA-256 of their JSON, in one
 * spelling, is the same.
 *
 * <p>Jackson with sorted map keys writes the JSON Canonicalization Scheme (RFC 8785) for all that a
 * node holds: objects, arrays, strings, integers and null. It is different from JCS only for
 * floating point numbers, and for the control characters that XML 1.0 does not allow. Nodes cannot
 * hold these.
 */
final class Identity {
  private static final JsonMapper JSON =
      JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

  private Identity() {}

  static byte[] bytes(Object json) {
    return JSON.writeValueAsBytes(json);
  }

  static ContentHash of(byte[] bytes) {
    try {
      return ContentHash.of(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JDK has SHA-256", e);
    }
  }

  static ContentHash of(Object json) {
    return of(bytes(json));
  }

  /**
   * Reads back JSON as stored. The result holds lists, maps in their order, strings, integers and
   * null.
   */
  static Object parse(byte[] json) {
    return JSON.readValue(json, Object.class);
  }
}
