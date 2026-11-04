// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * One of an act's own dates, as Cellar types it: when it enters into force, applies or takes
 * effect, wholly or in part, with the clause that sets it. Cellar keeps these up to date when a
 * later act moves one (2019/103 applies in part from 31 December 2021, its Article 2 as amended by
 * 2020/910), which its per-provision dates aren't always (Research: where a provision's date comes
 * from).
 *
 * @param date the day; none where Cellar has a placeholder or an impossible date, its clause still
 *     saying where the act sets it
 * @param kind what happens on it
 * @param partial whether it is for part of the act only (Cellar's MA/PART)
 * @param clause what sets it
 */
@ValueObject
public record ActDate(@Nullable LocalDate date, Kind kind, boolean partial, Clause clause) {

  /** What happens on an act's date. */
  public enum Kind {
    /** The act enters into force (Cellar's EV). */
    ENTRY_INTO_FORCE,
    /** It applies (MA). */
    APPLICATION,
    /** It takes effect, as a decision does on its notification (PE). */
    TAKING_EFFECT
  }

  /**
   * What sets a date, as far as Cellar's comment on it says: a day the date counts from and how
   * many days after it, the provisions that set it, and the acts that changed it since.
   *
   * @param from the day it counts from; none when the clause names the date itself
   * @param days how many days after {@code from}; none for the day itself
   * @param provisions the act's provisions that set it (Article 2)
   * @param amendedBy later acts that changed it (2020/910 for 2019/103's)
   * @param text Cellar's comment as it is ("DATPUB +20 V ART 2"), whatever of it the other fields
   *     don't hold
   */
  @ValueObject
  public record Clause(
      @Nullable Anchor from,
      @Nullable Integer days,
      List<Provision> provisions,
      List<ActIdentifier> amendedBy,
      String text) {
    public Clause {
      provisions = List.copyOf(provisions);
      amendedBy = List.copyOf(amendedBy);
      if (days != null && from == null) {
        throw new IllegalArgumentException("Days after nothing: " + text);
      }
    }
  }

  /** A day a date counts from. */
  public enum Anchor {
    /** The act's publication in the Official Journal (Cellar's DATPUB). */
    PUBLICATION,
    /** Its notification to those it is addressed to (DATNOT). */
    NOTIFICATION,
    /** Its own date, when it was adopted or signed (DATDOC). */
    DOCUMENT
  }

  /**
   * A provision of the act: an article by its number (2, 2.2(f)), or an annex, by its number if it
   * has one.
   */
  @ValueObject
  public record Provision(Type type, @Nullable String number) {

    /** An article or an annex. */
    public enum Type {
      ARTICLE,
      ANNEX
    }

    /** As {@link #toString} writes it: "ARTICLE 2", "ANNEX". */
    public static Provision parse(String value) {
      var parts = value.split(" ", 2);
      return new Provision(Type.valueOf(parts[0]), parts.length > 1 ? parts[1] : null);
    }

    @Override
    public String toString() {
      return number == null ? type.name() : type.name() + " " + number;
    }
  }
}
