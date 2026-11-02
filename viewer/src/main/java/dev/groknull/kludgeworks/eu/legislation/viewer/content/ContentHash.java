// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.HexFormat;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A node's address: the SHA-256 of its canonical content, as 64 lowercase hex digits. A version's
 * text is known by its root's.
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
