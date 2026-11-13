// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The state of the scan of Cellar for changes: how far it has read, which instance scans, and the
 * held acts that changed ({@link PendingChange}). The times of the claims come from the store, so
 * one clock measures them for all the instances.
 */
public interface ChangeStore {

  /**
   * Claims the next scan, if no instance claimed one in the last {@code every}. Thus one instance
   * scans at a time, and Cellar gets one query per interval, however many instances run. An
   * instance that stops is replaced after {@code every}.
   *
   * @return the claim, or none if another instance claimed the scan
   */
  Optional<Claim> claim(Duration every);

  /**
   * Moves the watermark to {@code watermark}, if {@code claim} is still the latest claim. It never
   * moves the watermark back.
   *
   * @return whether the claim held. If not, another instance scans, and the caller rolls back.
   */
  boolean advance(Claim claim, Instant watermark);

  /** Returns the pending changes of these acts. An act without one has none. */
  Map<ActIdentifier, PendingChange> pending(Collection<ActIdentifier> acts);

  /** Writes the pending changes. */
  void save(Collection<PendingChange> changes);

  /**
   * Returns the pending changes due at {@code now}, locked until the transaction ends. A change
   * that another transaction has locked is skipped.
   */
  List<PendingChange> due(Instant now);

  /**
   * A claim of the scan.
   *
   * @param watermark the latest date of a change that the scans have read, by Cellar's clock
   * @param claimedAt when it was claimed, by the store's clock. It identifies the claim.
   */
  record Claim(Instant watermark, Instant claimedAt) {}
}
