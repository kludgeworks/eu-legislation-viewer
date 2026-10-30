// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * An act a consolidated version integrates.
 *
 * @param type Amendment or corrigendum.
 * @param act The CELEX number of the amending act or corrigendum.
 * @param date The date of that act.
 */
public record EventRef(Type type, String act, @Nullable LocalDate date) {

  /** What kind of act a consolidated version integrates. */
  public enum Type {
    /** An amending act. */
    AMENDMENT,
    /** A corrigendum, correcting an error in the published text. */
    CORRIGENDUM
  }
}
