// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * How an act an act's versions integrate was published, as the Publications Office's catalogue has
 * it (Design D-36, D-42, D-21): its ELI, its titles, and its place in the Official Journal.
 *
 * @param eli its ELI; none where Cellar has none
 * @param titles its title in each official language Cellar has it in
 * @param journal where the Official Journal published it; none where Cellar doesn't say
 */
@ValueObject
public record Publication(
    @Nullable Eli eli, Map<Language, String> titles, @Nullable Journal journal) {

  public Publication {
    titles = Map.copyOf(titles);
  }

  /** Its title in {@code language}; none if Cellar has none in it. */
  public Optional<String> title(Language language) {
    return Optional.ofNullable(titles.get(language));
  }

  /**
   * An act's place in the Official Journal: its series, and either its number in the act-by-act
   * Journal (from October 2023) or the issue it is in, and the day it was published.
   *
   * @param series the series: L, C
   * @param number its number in the act-by-act Journal: 2026/449; none in an issue
   * @param issue the issue it is in: 21; none in the act-by-act Journal
   * @param published the day the Journal published it
   */
  @ValueObject
  public record Journal(
      String series, @Nullable String number, @Nullable Integer issue, LocalDate published) {

    /**
     * The reference, as the Official Journal cites it: OJ L, 2026/449, 2.3.2026 for an act of the
     * act-by-act Journal; OJ L 21, 24.1.2019 for one of an issue.
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
