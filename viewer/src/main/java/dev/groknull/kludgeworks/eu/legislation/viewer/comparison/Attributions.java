// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Who made each difference of a comparison, and from when each change applies. The sidenotes of the
 * differences say it in words.
 */
public interface Attributions {

  /**
   * Returns the comparison of the version {@code from} of {@code act} with the version {@code to},
   * each difference with who made it ({@link Attributed#of}). The two versions are of {@code
   * versions}, the versions of {@code act} in a language.
   *
   * <ul>
   *   <li>Cellar's amendments of the occurrences of the two texts give the acts, with the tags of
   *       the later text ({@link WhoMadeAChange#amendmentsAroundAndInside}).
   *   <li>Where no act is named, the difference names the text where the change first appears
   *       ({@link Comparisons#appeared}).
   *   <li>With {@code day}, the later day of the comparison, a difference has the change that does
   *       not apply on that day yet ({@code Act.prospective}).
   * </ul>
   *
   * <p>Returns none if either text is not built.
   */
  Optional<Compared> of(
      Act act, Versions versions, Version from, Version to, @Nullable LocalDate day);

  /**
   * A comparison, each difference with who made it.
   *
   * @param comparison the comparison
   * @param later the later text
   * @param earlier the outline of the earlier text
   * @param outline the outline of the later text
   * @param amended Cellar's amendments between the two versions, at the occurrences of their texts
   * @param each each difference with who made it, in the order of the comparison
   */
  record Compared(
      Comparison comparison,
      TextId later,
      Outline earlier,
      Outline outline,
      Amended amended,
      List<Attributed> each) {

    public Compared {
      each = List.copyOf(each);
    }

    /**
     * Returns {@code difference}, a difference of the comparison, with who made it. A comparison
     * has one difference for each occurrence, so the eId of its occurrence finds it.
     */
    public Attributed of(Difference difference) {
      for (var attributed : each) {
        if (attributed.difference().eId().equals(difference.eId())) {
          return attributed;
        }
      }
      throw new IllegalArgumentException("Not a difference of the comparison: " + difference.eId());
    }
  }
}
