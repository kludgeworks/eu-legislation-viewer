// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.WhatThePageShows.Shown;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * What the differences that a page shows have in them, for its summary and its legend.
 *
 * <ul>
 *   <li>The legend names only the edits that the page shows: inserted words, deleted words, and the
 *       words of a correction.
 *   <li>The words of a correction count only as a correction, not as inserted or deleted words.
 *   <li>If one known act made every difference, the summary names it. If a difference has no known
 *       act, or two acts made the differences, it names none.
 * </ul>
 *
 * @param inserted whether the page shows inserted words
 * @param deleted whether the page shows deleted words
 * @param corrected whether the page shows the words of a correction
 * @param by the one act that made every difference. Null if there is none.
 */
@ValueObject
public record Summary(
    boolean inserted, boolean deleted, boolean corrected, @Nullable ActIdentifier by) {

  /**
   * Returns the summary of the differences {@code shown}, with who made each ({@link Attributed}),
   * in the order that the page shows them. It applies the rules of {@link WhatThePageShows}.
   */
  public static Summary of(List<Attributed> shown) {
    var legend =
        WhatThePageShows.legendNamesTheEditsShown(
            shown.stream()
                .map(each -> new Shown(each.difference(), each.attribution().corrected()))
                .toList());
    var by =
        WhatThePageShows.actThatMadeEveryDifference(
            shown.stream().map(each -> each.attribution().acts()).toList());
    return new Summary(legend.inserted(), legend.deleted(), legend.corrected(), by.orElse(null));
  }
}
