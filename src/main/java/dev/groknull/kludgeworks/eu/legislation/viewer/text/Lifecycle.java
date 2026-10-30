// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A consolidated version's period and the acts it integrates (Formex INFO.CONSLEG, GR.MOD.ACT,
 * GR.CORRIG). What its dates mean in law (entry into force, application) comes from Cellar, not
 * from here.
 *
 * @param events An act this version integrates, in the order the source lists them.
 * @param start The first day this version applies to.
 * @param end The last day this version applies to; absent while it is the latest.
 * @param produced When the Publications Office produced the consolidation.
 * @param sequence The production sequence number of the consolidation, e.g. 023.001.0.
 */
public record Lifecycle(
    List<EventRef> events,
    @Nullable LocalDate start,
    @Nullable LocalDate end,
    @Nullable LocalDate produced,
    @Nullable String sequence) {

  public Lifecycle {
    events = List.copyOf(events);
  }
}
