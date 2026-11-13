// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The identity of a stored text. Postgres gives it when the text is stored, and it is never used
 * again. A text that is stored again with another content gets a new identity. Thus an identity
 * always names the same text, and what is kept by it, for example a comparison, needs no
 * invalidation.
 */
@ValueObject
public record TextId(long value) {

  /** Reads the identity as {@link #toString} writes it, for example in a URL. */
  public static TextId valueOf(String value) {
    return new TextId(Long.parseLong(value));
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
