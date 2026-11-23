// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The differences that an act made, or another act of the text that first includes it, where the
 * data does not say which act. These are the differences that one of two acts or more made ({@link
 * Attribution#unnamed}), where the act is one of them. The page of the act lists them apart from
 * its own changes.
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

  /** Returns the differences of {@code attributed} that {@code act} or another act made. */
  @BusinessRule(group = RuleGroup.THE_CHANGES_OF_ONE_ACT, name = "this-act-or-another")
  public static Alternatives of(ActIdentifier act, List<Summary.Attributed> attributed) {
    var differences = new ArrayList<Difference>();
    var others = new LinkedHashSet<ActIdentifier>();
    for (var each : attributed) {
      var candidates = each.attribution().candidates();
      if (candidates.contains(act)) {
        differences.add(each.difference());
        candidates.stream().filter(other -> !other.equals(act)).forEach(others::add);
      }
    }
    return new Alternatives(differences, List.copyOf(others));
  }
}
