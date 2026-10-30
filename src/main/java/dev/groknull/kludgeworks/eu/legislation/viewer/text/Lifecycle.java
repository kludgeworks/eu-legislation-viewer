// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A consolidated version's period and the acts it integrates, in document order. */
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
