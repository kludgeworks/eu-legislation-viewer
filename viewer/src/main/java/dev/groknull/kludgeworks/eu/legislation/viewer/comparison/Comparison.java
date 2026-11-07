// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * How one text differs from another.
 *
 * @param from the earlier text's root
 * @param to the later text's root
 * @param differences the provisions that differ, in the later text's order, a deleted one where it
 *     stood
 * @param closing what the later text has after the last difference (all of it, where there are
 *     none), as a difference's context has it
 * @param withoutPreamble the text that has no preamble, where the other has one: the Publications
 *     Office publishes some consolidations without it, which no act did, so it isn't compared; none
 *     where both have one or neither does
 */
@ValueObject
public record Comparison(
    ContentHash from,
    ContentHash to,
    List<Difference> differences,
    List<Context> closing,
    @Nullable Side withoutPreamble) {
  public Comparison {
    differences = List.copyOf(differences);
    closing = List.copyOf(closing);
  }

  /** One of the two texts compared. */
  public enum Side {
    /** The earlier text, From's. */
    EARLIER,
    /** The later text, To's. */
    LATER
  }
}
