// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * What another act did to one of an act's provisions, as Cellar annotates the amendment. It tells
 * where, what, and from when.
 *
 * <p>Its day is nearly always one of the own dates of the amending act, in 98% of the amendments.
 * But the day can be stale where a later act moved that date. For example, the points of 2019/103
 * in 2015/1998 are stale after 2020/910. Thus the day is used only to choose among the own dates of
 * the amending act.
 *
 * @param act the amending act
 * @param location where, in Cellar's codes
 * @param role what it did
 * @param from the day it applies from, as Cellar has it, or null where Cellar has none
 * @param until the day it ends, for an amendment that lapses, or null for the others
 * @param languages the only language versions it changes, for an act that corrects only some of
 *     them. For example, 2017/837 corrects the Swedish and the Polish versions. The set is empty
 *     for an amendment that changes all the language versions.
 */
@ValueObject
public record Amendment(
    ActIdentifier act,
    Location location,
    Role role,
    @Nullable LocalDate from,
    @Nullable LocalDate until,
    Set<Language> languages) {

  public Amendment {
    languages = Set.copyOf(languages);
  }

  /**
   * Returns whether it changes the text in {@code language}. It must change the words, not only
   * when they apply.
   */
  public boolean changes(Language language) {
    return role.wording() && (languages.isEmpty() || languages.contains(language));
  }

  /** What an amendment did, as Cellar types it in {@code fd_375}. */
  public enum Role {
    /** Replaced it, wholly or in part. Cellar's codes {@code R} and {@code R/PART}. */
    REPLACEMENT(true, "R", "R/PART"),
    /** Added it, or added to it. Cellar's code {@code J}, and {@code C} for a completion. */
    ADDITION(true, "J", "C"),
    /** Deleted it. Cellar's code {@code DEL}, and {@code SU} for an abolition. */
    DELETION(true, "DEL", "SU"),
    /** Repealed it, wholly or in part. Cellar's codes {@code A} and {@code AP}. */
    REPEAL(true, "A", "AP"),
    /** Amended it. Cellar's code {@code M}. */
    AMENDMENT(true, "M"),
    /**
     * Extended how long it applies. Cellar's codes {@code P} and {@code P/PART}, and {@code PI} for
     * an implicit extension.
     */
    EXTENSION(false, "P", "P/PART", "PI"),
    /** Suspended it, wholly or in part. Cellar's codes {@code S} and {@code SP}. */
    SUSPENSION(false, "S", "SP"),
    /** Deferred when it applies. Cellar's code {@code T}. */
    DEFERRAL(false, "T"),
    /** Anything else Cellar may type an amendment as. */
    OTHER(false);

    private final boolean wording;
    private final List<String> codes;

    Role(boolean wording, String... codes) {
      this.wording = wording;
      this.codes = List.of(codes);
    }

    /** Returns whether it changes the words, rather than when they apply. */
    public boolean wording() {
      return wording;
    }

    /** Returns whether it takes the provision out of the text, as a deletion or a repeal does. */
    public boolean removes() {
      return this == DELETION || this == REPEAL;
    }

    /**
     * Returns the role for Cellar's code {@code code}, or empty for a code that it does not know.
     */
    public static Optional<Role> of(String code) {
      return Arrays.stream(values()).filter(role -> role.codes.contains(code)).findFirst();
    }
  }
}
