// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * One of an act's own dates, as Cellar types it. It is the date when the act enters into force,
 * applies or takes effect, wholly or in part. It comes with the clause that sets it.
 *
 * <p>Cellar keeps these dates up to date when a later act moves one. For example, 2019/103 applies
 * in part from 31 December 2021, by its Article 2 as amended by 2020/910. The per-provision dates
 * of Cellar are not always up to date in this way.
 *
 * @param date the day, or null where Cellar has a placeholder or an impossible date. The clause
 *     still tells where the act sets the date.
 * @param kind what happens on it
 * @param partial whether it is for part of the act only. Cellar's code is {@code MA/PART}.
 * @param clause what sets it
 */
@ValueObject
public record ActDate(@Nullable LocalDate date, Kind kind, boolean partial, Clause clause) {

  /** What happens on an act's date. */
  public enum Kind {
    /** The act enters into force. Cellar's code {@code EV}. */
    ENTRY_INTO_FORCE,
    /** It applies. Cellar's code {@code MA}. */
    APPLICATION,
    /** It takes effect, as a decision does on its notification. Cellar's code {@code PE}. */
    TAKING_EFFECT
  }

  /**
   * What sets a date, as far as Cellar's comment on it says. It holds the day that the date counts
   * from, how many days after that day, the provisions that set it, and the acts that changed it
   * since.
   *
   * @param from the day it counts from, or null when the clause names the date itself
   * @param days how many days after {@code from}, or null for the day itself
   * @param provisions the act's provisions that set it, for example Article 2
   * @param amendedBy later acts that changed it. For example, 2020/910 changed a date of 2019/103.
   * @param text Cellar's comment as it is, for example {@code DATPUB +20 V ART 2}. It also holds
   *     what the other fields do not hold.
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
    /** The act's publication in the Official Journal. Cellar's code {@code DATPUB}. */
    PUBLICATION,
    /** Its notification to those it is addressed to. Cellar's code {@code DATNOT}. */
    NOTIFICATION,
    /** Its own date, when it was adopted or signed. Cellar's code {@code DATDOC}. */
    DOCUMENT
  }

  /**
   * A provision of the act. It is an article by its number, for example 2 or 2.2(f). Or it is an
   * annex, by its number if it has one.
   */
  @ValueObject
  public record Provision(Type type, @Nullable String number) {

    /** An article or an annex. */
    public enum Type {
      ARTICLE,
      ANNEX
    }

    /** Returns how the act cites the provision. For example, "Article 2", "Annex" or "Annex II". */
    public String cited() {
      var name = type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT);
      return number == null ? name : name + " " + number;
    }

    /**
     * Returns the provision from the form that {@link #toString} writes, for example "ARTICLE 2" or
     * "ANNEX".
     */
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
