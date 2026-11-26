// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The law of an act on a day: the versions that apply on it, the day they apply until, the deferred
 * application of the act itself, and where their text is not the law on the day. {@link Laws#on}
 * gives it for consumers such as the API.
 *
 * @param act the act
 * @param day the day asked
 * @param applying the versions that apply on {@code day}
 * @param until the day the next version starts, or null if no version starts later
 * @param deferred the deferred application of the act itself on {@code day}, or null if it applies
 *     in full
 * @param divergences where the text of the versions is not the law on {@code day}
 */
public record TheLaw(
    Act act,
    LocalDate day,
    List<Version> applying,
    @Nullable LocalDate until,
    @Nullable Deferred deferred,
    List<Diverged> divergences) {

  public TheLaw {
    applying = List.copyOf(applying);
    divergences = List.copyOf(divergences);
  }

  /**
   * The deferred application of the act on the day: the act is in force, but it does not apply in
   * full yet.
   *
   * @param application the deferred application
   * @param languages the languages in which the reader gets a text on the day
   */
  public record Deferred(DeferredApplication application, List<Language> languages) {
    public Deferred {
      languages = List.copyOf(languages);
    }
  }

  /**
   * Where the text is not the law on a day: a pending or prospective amendment.
   *
   * @param divergence the divergence
   * @param languages the languages whose text differs
   */
  public record Diverged(Divergence divergence, List<Language> languages) {
    public Diverged {
      languages = List.copyOf(languages);
    }
  }
}
