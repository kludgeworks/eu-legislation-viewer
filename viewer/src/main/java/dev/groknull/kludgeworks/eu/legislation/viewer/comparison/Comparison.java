// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * How one text differs from another.
 *
 * @param from the earlier text
 * @param to the later text
 * @param differences the provisions that differ, in the order of the later text. A deleted
 *     provision is where it stood.
 * @param closing what the later text has after the last difference, in the same form as the context
 *     of a difference. If there are no differences, this is all of the later text.
 * @param withoutPreamble the text that has no preamble, if the other text has one. The Publications
 *     Office publishes some consolidations without the preamble, although no act removed it. Thus
 *     the preamble is not compared. Null if both texts have one, or if neither has one.
 */
@ValueObject
public record Comparison(
    TextId from,
    TextId to,
    List<Difference> differences,
    List<Context> closing,
    @Nullable Side withoutPreamble) {
  public Comparison {
    differences = List.copyOf(differences);
    closing = List.copyOf(closing);
  }

  /** One of the two texts compared. */
  public enum Side {
    /** The earlier text, the text of From. */
    EARLIER,
    /** The later text, the text of To. */
    LATER
  }
}
