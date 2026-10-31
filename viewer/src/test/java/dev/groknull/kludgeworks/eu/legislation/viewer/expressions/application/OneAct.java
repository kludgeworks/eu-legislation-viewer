// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 2015/1998 and its two versions; no other act. */
final class OneAct implements Acts {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final Version ORIGINAL = Version.original(LocalDate.of(2015, 11, 15), AVSEC);
  static final Version CONSOLIDATED =
      new Version(
          Version.Kind.CONSOLIDATED,
          LocalDate.of(2026, 3, 22),
          "02015R1998-20260322",
          false,
          List.of(new ActIdentifier("32026R0449")));

  static final Act ACT =
      new Act(
          AVSEC,
          new Eli("reg_impl", 2015, "1998"),
          new ResourceType("REG_IMPL"),
          LocalDate.of(2015, 11, 5),
          true,
          null,
          new Timeline(List.of(ORIGINAL, CONSOLIDATED)));

  @Override
  public Optional<Act> find(ActIdentifier id) {
    return Optional.of(ACT).filter(act -> act.id().equals(id));
  }

  @Override
  public Optional<Act> find(Eli eli) {
    return Optional.empty();
  }
}
