// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheTextAndTheLawOnADay.NotTheLaw;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The text that a reader gets on a day, and where it is not the law on that day, as the rules give
 * it. {@link LawOverTime#on} gives it. A page and the API show the same answer: each only puts it
 * into its own form.
 *
 * @param day the day
 * @param version the version that the reader gets, or null before the first version of the act. The
 *     version can lack the language.
 * @param madeBy the acts that made the text of {@code version} in the language, in the order of the
 *     Publications Office
 * @param shown the version whose text a comparison shows for the day, or null before the first
 *     version in the language
 * @param notTheLaw the acts for which the text of the day is not the law
 */
public record LawOnADay(
    LocalDate day,
    @Nullable Version version,
    List<ActIdentifier> madeBy,
    @Nullable Version shown,
    NotTheLaw notTheLaw) {

  public LawOnADay {
    madeBy = List.copyOf(madeBy);
  }

  /** Returns the deferred application of the act itself on the day, or null if it has none. */
  public @Nullable DeferredApplication deferredApplication() {
    return notTheLaw.deferred();
  }

  /** Returns the pending and the prospective amendments of the day. */
  public List<Divergence> divergences() {
    return notTheLaw.divergences();
  }
}
