// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * An act a consolidated version integrates.
 *
 * @param type Amendment or corrigendum.
 * @param act The CELEX number of the amending act or corrigendum.
 * @param date The date of that act; for a corrigendum, which has no date of its own, the day it was
 *     published.
 */
public record EventRef(Type type, ActIdentifier act, @Nullable LocalDate date) {

  /** What kind of act a consolidated version integrates. */
  public enum Type {
    /** An amending act. */
    AMENDMENT,
    /** A corrigendum, correcting an error in the published text. */
    CORRIGENDUM
  }
}
