// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * How an act, or an act that its versions integrate, was published, as the catalogue of the
 * Publications Office has it. It holds the ELI, the titles, and the place in the Official Journal.
 *
 * @param eli its ELI, or null where Cellar has none
 * @param titles its title in each official language that Cellar has it in
 * @param journal where the Official Journal published it, or null where Cellar does not say
 */
@ValueObject
public record Publication(
    @Nullable Eli eli, Map<Language, String> titles, @Nullable Journal journal) {

  public Publication {
    titles = Map.copyOf(titles);
  }

  /** Returns its title in {@code language}, or empty if Cellar has no title in that language. */
  public Optional<String> title(Language language) {
    return Optional.ofNullable(titles.get(language));
  }

  /**
   * An act's place in the Official Journal. It holds the series and the day it was published. It
   * also holds either its number in the act-by-act Journal, which started in October 2023, or the
   * issue that it is in.
   *
   * @param series the series, for example L or C
   * @param number its number in the act-by-act Journal, for example 2026/449. It is null for an act
   *     in an issue.
   * @param issue the issue that it is in, for example 21. It is null in the act-by-act Journal.
   * @param published the day the Journal published it
   */
  @ValueObject
  public record Journal(
      String series, @Nullable String number, @Nullable Integer issue, LocalDate published) {

    /**
     * Returns the reference, as the Official Journal cites it. For an act of the act-by-act
     * Journal, it is for example "OJ L, 2026/449, 2.3.2026". For an act in an issue, it is for
     * example "OJ L 21, 24.1.2019".
     */
    public String reference() {
      var day =
          published.getDayOfMonth() + "." + published.getMonthValue() + "." + published.getYear();
      return number != null
          ? "OJ " + series + ", " + number + ", " + day
          : "OJ " + series + (issue == null ? "" : " " + issue) + ", " + day;
    }
  }
}
