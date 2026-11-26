// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline.TwoDays;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The comparisons of the law of an act on two days. The Compare page asks here, and only here, for
 * what it shows.
 *
 * <p>Each method gets the act without waiting for the Publications Office: it throws {@link
 * NotCatalogued} if the viewer looks the act up, and {@link NoSuchAct} if no act has the
 * identifier.
 */
public interface ComparedLaws {

  /**
   * Returns what a reader gets who asks to compare the law of the act on {@code from} and on {@code
   * to}, in {@code language}. It starts the preparation of the act if it is not prepared. Empty if
   * the act has no text in the language, or no text for one of the days.
   *
   * @param occurrence the eId of the provision that the comparison is limited to, or null
   */
  Optional<ComparedLaw> of(
      Eli act,
      Language language,
      @Nullable LocalDate from,
      @Nullable LocalDate to,
      @Nullable String occurrence);

  /**
   * Tries again to build the texts of the act that failed where a new try may help. Returns the two
   * days of the comparison. Empty if the act has no text in the language.
   */
  Optional<TwoDays> retry(Eli act, Language language, LocalDate from, LocalDate to);
}
