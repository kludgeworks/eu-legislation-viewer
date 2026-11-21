// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

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
}
