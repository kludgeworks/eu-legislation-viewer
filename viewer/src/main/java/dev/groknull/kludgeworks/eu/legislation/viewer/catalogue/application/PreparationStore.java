// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation.Entry;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The acts' preparations, each in a language, with their logs. */
public interface PreparationStore {

  /** Starts the act's preparation in the language, unless it has started. */
  void start(ActIdentifier act, Language language);

  /** Returns the languages that the act is prepared in, or is being prepared in. */
  List<Language> languages(ActIdentifier act);

  /** Returns when the preparation started, and when it settled. */
  Optional<Kept> find(ActIdentifier act, Language language);

  /**
   * Returns the same as {@link #find}, and locks its row until the transaction ends. Thus the
   * changes of its texts happen one at a time. It settles once, and the last change sees the
   * others.
   */
  Optional<Kept> lock(ActIdentifier act, Language language);

  /**
   * Settles it, as prepared or failed, unless it is settled.
   *
   * @return whether it settled now
   */
  boolean settle(ActIdentifier act, Language language, boolean prepared);

  /**
   * Starts the act's settled preparation in the language again, from now, and empties its log. It
   * does nothing if the act is being prepared.
   *
   * @return whether it started again
   */
  boolean restart(ActIdentifier act, Language language);

  /** Adds an entry to the log, if the act is being prepared in the language. */
  void entry(
      ActIdentifier act,
      Language language,
      String celex,
      Entry.Kind kind,
      @Nullable Integer attempt,
      @Nullable Failure failure);

  /**
   * Returns its log, in the order it was written. A built or failed entry has the attempt of the
   * last start of its text, and the time since that start.
   */
  List<Entry> entries(ActIdentifier act, Language language);

  /** A preparation as stored. */
  record Kept(Instant started, @Nullable Instant settledAt) {}
}
