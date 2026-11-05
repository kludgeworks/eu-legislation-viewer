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
 * What another act did to one of an act's provisions, as Cellar annotates the amendment (Research:
 * Amendment annotations): where, what, and from when. Its day is one of the amending act's own
 * dates nearly always (98% of them), but it can be stale where a later act moved that date
 * (2019/103's points in 2015/1998, after 2020/910), so it only chooses among them.
 *
 * @param act the amending act
 * @param location where, in Cellar's codes
 * @param role what it did
 * @param from the day it applies from, as Cellar has it; none where Cellar has none
 * @param until the day it ends, for one that lapses; none for the others
 * @param languages the only language versions it changes, for an act that corrects some (2017/837,
 *     the Swedish and Polish); empty for one that changes them all
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

  /** Whether it changes the text in {@code language}: its words, not only when they apply. */
  public boolean changes(Language language) {
    return role.wording() && (languages.isEmpty() || languages.contains(language));
  }

  /** What an amendment did, as Cellar types it (fd_375). */
  public enum Role {
    /** Replaced it, wholly or in part (R, R/PART). */
    REPLACEMENT(true, "R", "R/PART"),
    /** Added it, or to it (J; C, completion). */
    ADDITION(true, "J", "C"),
    /** Deleted it (DEL; SU, abolition). */
    DELETION(true, "DEL", "SU"),
    /** Repealed it, wholly or in part (A, AP). */
    REPEAL(true, "A", "AP"),
    /** Extended how long it applies (P, P/PART; PI, implicitly). */
    EXTENSION(false, "P", "P/PART", "PI"),
    /** Suspended it, wholly or in part (S, SP). */
    SUSPENSION(false, "S", "SP"),
    /** Deferred when it applies (T). */
    DEFERRAL(false, "T"),
    /** Anything else Cellar may type an amendment as. */
    OTHER(false);

    private final boolean wording;
    private final List<String> codes;

    Role(boolean wording, String... codes) {
      this.wording = wording;
      this.codes = List.of(codes);
    }

    /** Whether it changes the words, rather than when they apply. */
    public boolean wording() {
      return wording;
    }

    /** Whether it takes the provision out of the text: a deletion or a repeal. */
    public boolean removes() {
      return this == DELETION || this == REPEAL;
    }

    /** The role Cellar's code {@code code} is; none for one this doesn't know. */
    public static Optional<Role> of(String code) {
      return Arrays.stream(values()).filter(role -> role.codes.contains(code)).findFirst();
    }
  }
}
