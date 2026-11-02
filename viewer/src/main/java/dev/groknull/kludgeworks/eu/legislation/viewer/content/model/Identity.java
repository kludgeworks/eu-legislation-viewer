// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * What makes two nodes the same: the SHA-256 of their JSON in one spelling. Jackson with map keys
 * sorted writes the JSON Canonicalization Scheme (RFC 8785) for all a node holds (objects, arrays,
 * strings, integers, null); it parts from JCS only on floating point numbers, and on control
 * characters XML 1.0 doesn't allow, which nodes can't hold.
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

  /** JSON as stored, read back: lists, maps (in their order), strings, integers and null. */
  static Object parse(byte[] json) {
    return JSON.readValue(json, Object.class);
  }
}
