// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import java.time.Instant;
import java.util.List;

/** Where the changes come from. This is Cellar, the system of record. */
public interface ChangeSource {

  /**
   * Returns the works that Cellar changed after {@code since}: the acts, corrigenda and
   * consolidations, and those whose expressions changed. Each comes with the acts it is based on,
   * amends or corrects. It is one query.
   */
  List<CellarChange> since(Instant since);
}
