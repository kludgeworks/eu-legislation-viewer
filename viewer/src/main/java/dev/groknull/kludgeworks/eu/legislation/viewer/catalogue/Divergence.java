// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Where the text of an act is not the law. The text that a reader gets on a day is always a version
 * of the act, unchanged. A divergence tells on which days that text and the law differ, and why.
 *
 * <p>For example, 2017/815 applies from 1 June 2017, and the first version of 2015/1998 that
 * includes it starts on 7 June 2017. The text is late from 1 to 6 June 2017.
 *
 * @param kind whether the text is late or early
 * @param act the act that the text lacks, for a late text. For an early text, the act whose text
 *     does not apply yet.
 * @param applies the date from which {@code act} applies, with the clause that sets it
 * @param included the first version that includes the text of {@code act}. Null for a late text
 *     that no version includes yet.
 * @param places the provisions where the text differs, as Cellar annotates them. Empty where the
 *     whole act differs, or where Cellar does not say where.
 */
@ValueObject
public record Divergence(
    Kind kind,
    ActIdentifier act,
    ActDate applies,
    @Nullable Version included,
    List<Amendment> places) {

  public Divergence {
    places = List.copyOf(places);
    Objects.requireNonNull(applies.date(), "A divergence needs the day from which the act applies");
    if (kind == Kind.EARLY && included == null) {
      throw new IllegalArgumentException("An early text is in a version");
    }
  }

  /** How a text differs from the law. */
  public enum Kind {
    /** The act applies, but the text does not include it. */
    LATE,
    /** The text is included, but it does not apply yet. */
    EARLY
  }

  /** Returns the day from which {@code act} applies. */
  public LocalDate appliesFrom() {
    return Objects.requireNonNull(applies.date());
  }

  /**
   * Returns whether the text and the law differ on {@code day}.
   *
   * <ul>
   *   <li>A late text differs from the day the act applies to the day before the version that
   *       includes it. If no version includes it, it differs on each day from the day it applies.
   *   <li>An early text differs from the first day of the version that includes it to the day
   *       before the act applies.
   * </ul>
   */
  public boolean on(LocalDate day) {
    return switch (kind) {
      case LATE ->
          !day.isBefore(appliesFrom()) && (included == null || day.isBefore(included.date()));
      case EARLY ->
          !day.isBefore(Objects.requireNonNull(included).date()) && day.isBefore(appliesFrom());
    };
  }
}
