// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The two texts of the changes of one act (R-SCOPE-10): the first text that includes the act, and
 * the text just before it. A comparison limited to one act shows the changes between these two
 * texts. So the changes of two acts that one text first includes are the same changes. The
 * sidenotes tell them apart where they can (R-WHO-04, R-WHO-05).
 *
 * @param before the text just before {@code first}. It is {@code first} itself if no text is before
 *     it.
 * @param first the first text that includes the act, after the earlier day and up to the later day
 */
@ValueObject
public record Inclusion(Version before, Version first) {

  /**
   * Returns the texts of the changes of {@code act}, in the comparison of the version {@code from}
   * with the version {@code to}. Empty if no version after {@code from}, up to {@code to}, includes
   * the act.
   */
  public static Optional<Inclusion> of(
      Timeline timeline, Inclusions included, Version from, Version to, ActIdentifier act) {
    return timeline.versions().stream()
        .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
        .filter(v -> included.includes(v, act))
        .findFirst()
        .map(first -> new Inclusion(timeline.predecessor(first).orElse(first), first));
  }
}
