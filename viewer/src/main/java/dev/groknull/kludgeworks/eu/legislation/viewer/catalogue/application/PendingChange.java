// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * What the scan of Cellar saw change about a held act, while it waits to be read again. Cellar
 * publishes a change bit by bit: a new consolidation reaches its languages one by one, 7 to 10
 * minutes apart. So an act is read again only once it is quiet: {@link Debounce#quiet} after its
 * latest change, and at most {@link Debounce#atMost} after its first.
 *
 * <p>Cellar shows a change up to {@link Debounce#lag} after its date. So the scan reads each change
 * again on each scan for a while, because it reads with an overlap, and an act is read again no
 * sooner than {@link Debounce#lag} after the date of its latest change. Thus a read has each change
 * dated {@link Debounce#lag} before it or earlier, and such a change seen again is not new. Nor is
 * a change dated before the latest one seen while the act waits ({@code modified}): it does not
 * delay the act.
 *
 * @param act the held act
 * @param modified the latest date of a change of the act ({@code cmr:lastModificationDate}) seen
 *     since {@code firstSeen}. Null when nothing is waiting.
 * @param firstSeen when the scan first saw the change that is waiting. Null when nothing is
 *     waiting.
 * @param due when the act is to be read again. Null when nothing is waiting.
 * @param readAt when the act was last sent to be read again. Null if never.
 */
public record PendingChange(
    ActIdentifier act,
    @Nullable Instant modified,
    @Nullable Instant firstSeen,
    @Nullable Instant due,
    @Nullable Instant readAt) {

  public PendingChange {
    if ((firstSeen == null) != (due == null)) {
      throw new IllegalArgumentException("A waiting change has when it was seen and when it's due");
    }
  }

  /** An act that the scan never saw change. */
  static PendingChange none(ActIdentifier act) {
    return new PendingChange(act, null, null, null, null);
  }

  /** Returns whether a change is waiting. */
  boolean waiting() {
    return due != null;
  }

  /** Returns whether the act is to be read again at {@code now}. */
  boolean isDue(Instant now) {
    return due != null && !due.isAfter(now);
  }

  /**
   * Returns this after the scan saw, at {@code now}, a change of the act dated {@code changed}.
   *
   * <ul>
   *   <li>A change dated {@link Debounce#lag} or more before the last read is unchanged: it was in
   *       that read.
   *   <li>The first change makes the act wait {@link Debounce#quiet}, and until Cellar shows every
   *       change dated up to it.
   *   <li>A later change makes it wait so again, but no longer than {@link Debounce#atMost} after
   *       the first.
   *   <li>A change seen already, or dated before the latest seen, does not make it wait longer.
   * </ul>
   */
  PendingChange seen(Instant changed, Instant now, Debounce debounce) {
    if (readAt != null && !changed.plus(debounce.lag()).isAfter(readAt)) {
      return this;
    }
    if (firstSeen == null) {
      return new PendingChange(act, changed, now, due(changed, now, now, debounce), readAt);
    }
    if (modified != null && !changed.isAfter(modified)) {
      return this;
    }
    return new PendingChange(
        act, changed, firstSeen, due(changed, now, firstSeen, debounce), readAt);
  }

  /**
   * Returns when an act whose latest change, dated {@code changed}, was seen at {@code now} is read
   * again: when it has been quiet, and Cellar shows every change dated up to {@code changed}, but
   * no later than {@link Debounce#atMost} after {@code firstSeen}.
   */
  private static Instant due(Instant changed, Instant now, Instant firstSeen, Debounce debounce) {
    var quiet = now.plus(debounce.quiet());
    var shown = changed.plus(debounce.lag());
    var ready = shown.isAfter(quiet) ? shown : quiet;
    var latest = firstSeen.plus(debounce.atMost());
    return ready.isBefore(latest) ? ready : latest;
  }

  /**
   * Returns this after the act was sent to be read again at {@code now}. Nothing is waiting any
   * more.
   */
  PendingChange read(Instant now) {
    return new PendingChange(act, null, null, null, now);
  }

  /**
   * Returns this with the act to be read again by {@code at}, as a version that went missing needs
   * a second read ({@link Refresh}). A change that is due earlier stays due then.
   */
  PendingChange recheck(Instant at, Instant now) {
    if (due == null) {
      return new PendingChange(act, modified, now, at, readAt);
    }
    return due.isAfter(at) ? new PendingChange(act, modified, firstSeen, at, readAt) : this;
  }

  /**
   * How long an act waits before it is read again.
   *
   * @param quiet how long after its latest change
   * @param atMost how long after its first change, at most
   * @param lag how long after its date Cellar may show a change, at most
   */
  record Debounce(Duration quiet, Duration atMost, Duration lag) {}
}
