// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * What last made a provision that is the same in both texts as it is. The Synopsis shows it beside
 * a neighbour.
 *
 * <p>It is the act that the texts include last, up to the later text, among the acts that the tags
 * of the provision name. The tags are those of the provision, or those of the occurrence that an
 * act put in its form as a whole ({@link Tag}). A corrigendum is left out. If no such act is
 * included, the provision is as in the original act.
 */
@ValueObject
public sealed interface LastChange {

  /**
   * An act last changed the provision.
   *
   * @param act the act
   * @param modification what the act did, as its tag says
   * @param applicability from when the change applies, or null where the act's dates say nothing
   */
  record ByAct(ActIdentifier act, Modification modification, @Nullable Applicability applicability)
      implements LastChange {}

  /**
   * No act changed the provision: it is as in the original act.
   *
   * @param applicability from when the original act applies, or null where its dates say nothing
   */
  record Original(@Nullable Applicability applicability) implements LastChange {}

  /**
   * Returns what last made the provision whose tags are {@code tags} as it is, in the texts of
   * {@code act} up to {@code to} ({@link Neighbours#lastChangeOfANeighbour}), with from when that
   * change applies.
   */
  static LastChange of(Act act, Inclusions included, Version to, List<Tag> tags) {
    var firstIncluded = new HashMap<ActIdentifier, LocalDate>();
    for (var tag : tags) {
      var each = new ActIdentifier(tag.act());
      included.timeline().versions().stream()
          .filter(version -> !version.date().isAfter(to.date()))
          .filter(version -> included.includes(version, each))
          .map(Version::date)
          .min(LocalDate::compareTo)
          .ifPresent(day -> firstIncluded.put(each, day));
    }
    return Neighbours.lastChangeOfANeighbour(tags, firstIncluded)
        .<LastChange>map(last -> {
          var each = new ActIdentifier(last.act());
          return new ByAct(
              each, last.modification(), Applicability.of(act, each, Set.of()).orElse(null));
        })
        .orElseGet(() -> new Original(Applicability.of(act, act.id(), Set.of()).orElse(null)));
  }
}
