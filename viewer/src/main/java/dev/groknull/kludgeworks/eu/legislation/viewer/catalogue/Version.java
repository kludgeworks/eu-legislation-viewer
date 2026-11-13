// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * @param formex the Formex of each language, as Cellar last gave it: what tells that Cellar
 *     republished it. A text of the version is made from the Formex, so the text changes with it. A
 *     language whose Formex was not read yet has none.
 */
@ValueObject
public record Version(
    Kind kind,
    LocalDate date,
    String celex,
    boolean interim,
    List<ActIdentifier> integrates,
    Set<Language> languages,
    Map<Language, Formex> formex) {

  public Version {
    integrates = List.copyOf(integrates);
    languages =
        Collections.unmodifiableSet(
            languages.isEmpty() ? EnumSet.noneOf(Language.class) : EnumSet.copyOf(languages));
    formex = formex.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(formex));
    if (!languages.containsAll(formex.keySet())) {
      throw new IllegalArgumentException("Formex of a language that the version is not in");
    }
    if (kind == Kind.ORIGINAL && (interim || !integrates.isEmpty())) {
      throw new IllegalArgumentException(
          "An original text is never interim and integrates nothing");
    }
  }

  /**
   * Returns the original text, which applies from {@code date}, in {@code languages}, with the
   * Formex of each.
   */
  public static Version original(
      LocalDate date, ActIdentifier act, Set<Language> languages, Map<Language, Formex> formex) {
    return new Version(Kind.ORIGINAL, date, act.celex(), false, List.of(), languages, formex);
  }

  /**
   * Returns the address of the version's text on EUR-Lex, by its {@code celex}, in {@code
   * language}. For a consolidated version, it is the consolidated text. For example, {@code
   * https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:02015R1998-20260322}.
   */
  public URI eurLex(Language language) {
    return EurLex.byCelex(celex, language);
  }

  /** Returns whether this version applies only after {@code today}. */
  public boolean isFuture(LocalDate today) {
    return date.isAfter(today);
  }

  /**
   * The Formex of a version in a language, as Cellar has it.
   *
   * @param fixity the digest of the SHA-1 fixities of its files, which changes when the content of
   *     one of them changes. Null where Cellar gives no fixities, as for most versions before 2024.
   * @param modified when Cellar last modified it. It moves when Cellar republishes it, and also
   *     when Cellar changes its metadata.
   */
  @ValueObject
  public record Formex(@Nullable String fixity, Instant modified) {

    /**
     * Returns whether {@code later} is this Formex republished: its fixity changed. Cellar has no
     * fixities for most Formex last modified before 2024, and has them for all modified since. So a
     * Formex without one that Cellar republishes gains one, which is a change too, and two without
     * one are the same. The modification alone tells nothing: it moves on a change of metadata too.
     */
    public boolean republishedAs(Formex later) {
      return !Objects.equals(fixity, later.fixity());
    }
  }

  /** What a version's text is. */
  public enum Kind {
    /** The text as published in the Official Journal. */
    ORIGINAL,
    /** A consolidation. It is the text with its amendments and corrections up to a date. */
    CONSOLIDATED
  }
}
