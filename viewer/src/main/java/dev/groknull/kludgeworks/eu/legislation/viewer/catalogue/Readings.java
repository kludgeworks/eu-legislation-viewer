// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The law of an act on a day, as a reader gets it. The Read page asks here, and only here, for what
 * it shows.
 *
 * <p>Each method gets the act without waiting for the Publications Office: it throws {@link
 * NotCatalogued} if the viewer looks the act up, and {@link NoSuchAct} if no act has the
 * identifier.
 */
public interface Readings {

  /**
   * Returns what a reader gets who asks for the law of the act on {@code day}, in {@code language}.
   * It starts the preparation of the act if it is not prepared. Empty if no version applies on the
   * day.
   */
  Optional<Reading> of(Eli act, Language language, LocalDate day);

  /**
   * Returns the page that the address of the act without a page opens, in {@code language}.
   *
   * @param on the day that the reader gave, or null
   */
  FirstPage first(Eli act, Language language, @Nullable LocalDate on);

  /**
   * Returns the history of the occurrence {@code occurrence} of the text that the reader gets on
   * {@code day}. Empty on a day before the act, if the text is not built, or if it does not have
   * the occurrence.
   */
  Optional<ProvisionOnADay> provision(Eli act, Language language, LocalDate day, String occurrence);

  /** Tries again to build the texts of the act that failed where a new try may help. */
  void retry(Eli act, Language language);
}
