// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The history of one provision of an act: the acts that changed it. {@link Act#historyOf} makes it.
 *
 * @param occurrence the eId of the occurrence of the text that is the provision
 * @param changes the changes that Cellar annotates at the provision, in a part of it that the text
 *     does not number, or at an occurrence that holds it. The newest act is first.
 * @param tagging the acts that the text tags the provision with, and that have no change in {@code
 *     changes}. The data does not say what each of them did. The newest act is first.
 */
@ValueObject
public record ProvisionHistory(
    String occurrence, List<Amendment> changes, List<ActIdentifier> tagging) {

  public ProvisionHistory {
    changes = List.copyOf(changes);
    tagging = List.copyOf(tagging);
  }

  /** Returns the acts that changed the provision, each one time. The newest act is first. */
  public List<ActIdentifier> acts() {
    var acts = new LinkedHashSet<ActIdentifier>();
    changes.forEach(change -> acts.add(change.act()));
    acts.addAll(tagging);
    return List.copyOf(acts);
  }

  /**
   * Returns the acts that changed the provision and that are among {@code acts}, for example the
   * amending acts between two days ({@link Act#amendingActsBetween}). The newest act is first.
   */
  @BusinessRule(
      group = RuleGroup.THE_HISTORY_OF_AN_ACT,
      name = "the-amending-acts-between-two-days")
  public List<ActIdentifier> actsAmong(Collection<ActIdentifier> acts) {
    return acts().stream().filter(acts::contains).toList();
  }
}
