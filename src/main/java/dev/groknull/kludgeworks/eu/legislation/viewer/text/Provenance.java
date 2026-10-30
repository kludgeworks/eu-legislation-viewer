// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import org.jspecify.annotations.Nullable;

/** An amending act that put a unit, block or span in its current form. */
public record Provenance(
    String amendingAct, Action action, @Nullable String location, @Nullable String marker) {

  /** What the amending act did. */
  public enum Action {
    REPLACED,
    INSERTED,
    DELETED
  }
}
