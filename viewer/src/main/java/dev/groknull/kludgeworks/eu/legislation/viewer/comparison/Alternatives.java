// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The differences that an act made, or another act of the text that first includes it, where the
 * data does not say which act. These are the differences that one of two acts or more made ({@link
 * WhoMadeAChange#noActNamed}), where the act is one of them. The page of the act lists them apart
 * from its own changes.
 *
 * @param differences the differences, in the order of the comparison
 * @param others the other acts of which one may have made them, in the order that they first come
 */
@ValueObject
public record Alternatives(List<Difference> differences, List<ActIdentifier> others) {
  public Alternatives {
    differences = List.copyOf(differences);
    others = List.copyOf(others);
  }
}
