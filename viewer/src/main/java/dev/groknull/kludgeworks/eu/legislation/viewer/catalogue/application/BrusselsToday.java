// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheVersionsOfAnAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Today;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Tells today from the clock of the viewer. It decides nothing: the rule gives the day. */
@Service
public class BrusselsToday implements Today {
  private final Clock clock;

  public BrusselsToday(Clock clock) {
    this.clock = clock;
  }

  @Override
  public LocalDate today() {
    return TheVersionsOfAnAct.today(clock.instant());
  }

  @Override
  public Instant now() {
    return clock.instant();
  }
}
