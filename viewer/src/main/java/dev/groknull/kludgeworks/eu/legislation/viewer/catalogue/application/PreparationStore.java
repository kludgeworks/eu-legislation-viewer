// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation.Step;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The acts' preparations, each in a language, with their steps. */
public interface PreparationStore {

  /** Starts the act's preparation in the language, unless it has started. */
  void start(ActIdentifier act, Language language);

  /** When it started, and settled. */
  Optional<Kept> find(ActIdentifier act, Language language);

  /**
   * As {@code find}, its row locked until the transaction ends: so its texts' changes are one at a
   * time, and it settles once, the last of them seeing the others.
   */
  Optional<Kept> lock(ActIdentifier act, Language language);

  /**
   * Settles it, as prepared or failed, unless it is settled.
   *
   * @return whether it settled now
   */
  boolean settle(ActIdentifier act, Language language, boolean prepared);

  /** Adds a step, if the act is being prepared in the language. */
  void step(
      ActIdentifier act,
      Language language,
      String celex,
      Step.Kind kind,
      @Nullable Integer attempt,
      @Nullable Failure failure);

  /**
   * Its steps, in the order they were taken: one built or failed with the attempt of, and how long
   * since, its text's last start.
   */
  List<Step> steps(ActIdentifier act, Language language);

  /** A preparation as kept. */
  record Kept(Instant started, @Nullable Instant settledAt) {}
}
