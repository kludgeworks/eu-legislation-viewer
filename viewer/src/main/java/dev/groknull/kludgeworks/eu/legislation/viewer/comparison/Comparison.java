// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How one text differs from another.
 *
 * @param from the earlier text's root
 * @param to the later text's root
 * @param differences the provisions that differ, in the later text's order, a deleted one where it
 *     stood
 */
@ValueObject
public record Comparison(ContentHash from, ContentHash to, List<Difference> differences) {
  public Comparison {
    differences = List.copyOf(differences);
  }
}
