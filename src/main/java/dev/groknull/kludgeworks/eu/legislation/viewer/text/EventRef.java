// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** An act a consolidation integrates. */
public record EventRef(Type type, String act, @Nullable LocalDate date) {

  /** What the act did. */
  public enum Type {
    AMENDMENT,
    CORRIGENDUM
  }
}
