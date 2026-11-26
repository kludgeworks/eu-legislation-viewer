// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Tells today, and now. A page and the API ask here, so they agree on the day: today is the day in
 * Brussels ({@link TheVersionsOfAnAct#today}).
 */
public interface Today {

  /** Returns today. */
  LocalDate today();

  /** Returns this instant. */
  Instant now();
}
