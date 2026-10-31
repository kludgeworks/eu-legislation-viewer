// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * One version of an act's text: the original text as published in the Official Journal, or one of
 * its consolidated versions. A version applies from its date until the next version's.
 *
 * @param kind the original text, or a consolidated version
 * @param date the day it applies from: the original's entry into force, a consolidation's date
 * @param celex the document holding the text: the act's CELEX for the original (32015R1998), the
 *     consolidation's for a consolidated version (02015R1998-20260322)
 * @param interim a consolidated version published without a PDF, which may still be revised
 * @param integrates the amending acts and corrigenda Cellar lists as consolidated at this step; for
 *     display only, since Cellar's lists aren't always exact
 * @param languages the languages its text is in, as Formex, the only text the viewer reads: its
 *     expressions in Cellar with a Formex manifestation (Research: which languages a version is
 *     in). Not always all 24: 2015/1998's have no Irish
 * @param formexModified when Cellar last modified the Formex of any of them, as it does when it
 *     republishes it: what a text of the version is made from, so it changes with it; none for a
 *     version without Formex
 */
@ValueObject
public record Version(
    Kind kind,
    LocalDate date,
    String celex,
    boolean interim,
    List<ActIdentifier> integrates,
    Set<Language> languages,
    @Nullable Instant formexModified) {

  public Version {
    integrates = List.copyOf(integrates);
    languages =
        Collections.unmodifiableSet(
            languages.isEmpty() ? EnumSet.noneOf(Language.class) : EnumSet.copyOf(languages));
    if (languages.isEmpty() != (formexModified == null)) {
      throw new IllegalArgumentException(
          "A version with Formex has when it was modified, and only one with Formex has");
    }
    if (kind == Kind.ORIGINAL && (interim || !integrates.isEmpty())) {
      throw new IllegalArgumentException(
          "An original text is never interim and integrates nothing");
    }
  }

  /**
   * The original text, applying from {@code date}, in {@code languages}, its Formex modified on
   * {@code formexModified}.
   */
  public static Version original(
      LocalDate date,
      ActIdentifier act,
      Set<Language> languages,
      @Nullable Instant formexModified) {
    return new Version(
        Kind.ORIGINAL, date, act.celex(), false, List.of(), languages, formexModified);
  }

  /** Whether this version only applies after {@code today}. */
  public boolean isFuture(LocalDate today) {
    return date.isAfter(today);
  }

  /** What a version's text is. */
  public enum Kind {
    /** The text as published in the Official Journal. */
    ORIGINAL,
    /** A consolidation: the text with its amendments and corrections up to a date. */
    CONSOLIDATED
  }
}
