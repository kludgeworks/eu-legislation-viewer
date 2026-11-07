// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
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
 * @param date the day it applies from. For the original, it is the entry into force. For a
 *     consolidated version, it is the date of the consolidation.
 * @param celex the document that holds the text. For the original, it is the act's CELEX, for
 *     example {@code 32015R1998}. For a consolidated version, it is the CELEX of the consolidation,
 *     for example {@code 02015R1998-20260322}.
 * @param interim whether it is a consolidated version published without a PDF, which may still be
 *     revised
 * @param integrates the amending acts and corrigenda that Cellar lists as consolidated at this
 *     step. They are for display only, because Cellar's lists are not always exact.
 * @param languages the languages that its text is in as Formex, the only text that the viewer
 *     reads. These are its expressions in Cellar that have a Formex manifestation. They are not
 *     always all 24. For example, the versions of 2015/1998 have no Irish.
 * @param formexModified when Cellar last modified the Formex of any of them, as it does when it
 *     republishes it. A text of the version is made from the Formex, so the text changes with it.
 *     It is null for a version without Formex.
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
   * Returns the original text, which applies from {@code date}, in {@code languages}. Its Formex
   * was modified at {@code formexModified}.
   */
  public static Version original(
      LocalDate date,
      ActIdentifier act,
      Set<Language> languages,
      @Nullable Instant formexModified) {
    return new Version(
        Kind.ORIGINAL, date, act.celex(), false, List.of(), languages, formexModified);
  }

  /** Returns whether this version applies only after {@code today}. */
  public boolean isFuture(LocalDate today) {
    return date.isAfter(today);
  }

  /** What a version's text is. */
  public enum Kind {
    /** The text as published in the Official Journal. */
    ORIGINAL,
    /** A consolidation. It is the text with its amendments and corrections up to a date. */
    CONSOLIDATED
  }
}
