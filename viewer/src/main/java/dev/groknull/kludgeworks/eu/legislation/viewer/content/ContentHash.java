// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.HexFormat;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The address of a node or of a chunk. It is the SHA-256 of the canonical content of the node, or
 * of the JSON of the chunk, as 64 lowercase hex digits. The text of a version is known by the hash
 * of the root of its tree.
 */
@ValueObject
public record ContentHash(String hex) {
  private static final Pattern HEX = Pattern.compile("[0-9a-f]{64}");

  public ContentHash {
    if (!HEX.matcher(hex).matches()) {
      throw new IllegalArgumentException("Not a SHA-256 in hex: " + hex);
    }
  }

  public static ContentHash of(byte[] bytes) {
    return new ContentHash(HexFormat.of().formatHex(bytes));
  }

  public byte[] bytes() {
    return HexFormat.of().parseHex(hex);
  }

  @Override
  public String toString() {
    return hex;
  }
}
