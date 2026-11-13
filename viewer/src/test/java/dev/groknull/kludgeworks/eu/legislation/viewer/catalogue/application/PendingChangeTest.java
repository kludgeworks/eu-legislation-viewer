// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The debounce of the changes of a held act: it is read again 10 quiet minutes after its latest
 * change, no sooner than 20 minutes after the date of that change, and at most 60 minutes after its
 * first. The scan sees each change again on each scan for a while, and that does not delay it.
 */
class PendingChangeTest {
  private static final ActIdentifier ACT = new ActIdentifier("32015R1998");
  private static final PendingChange.Debounce DEBOUNCE =
      new PendingChange.Debounce(
          Duration.ofMinutes(10), Duration.ofMinutes(60), Duration.ofMinutes(20));
  private static final Instant T = Instant.parse("2026-11-10T15:00:00Z");

  @Test
  void anActIsReadAgainOnceQuietAndOnceCellarShowsItsChange() {
    // A change dated 15:00, seen at 15:15: Cellar shows changes up to 20 minutes late.
    var seen = PendingChange.none(ACT).seen(T, at(15), DEBOUNCE);

    assertThat(seen.waiting()).isTrue();
    assertThat(seen.due()).isEqualTo(at(25));
    assertThat(seen.isDue(at(24))).isFalse();
    assertThat(seen.isDue(at(25))).isTrue();
  }

  @Test
  void aChangeSeenLateWaitsTheQuietTime() {
    // Seen at 15:18: 20 minutes after its date is earlier than 10 quiet minutes.
    assertThat(PendingChange.none(ACT).seen(T, at(18), DEBOUNCE).due()).isEqualTo(at(28));
  }

  @Test
  void theSameChangeSeenAgainDoesNotDelayTheAct() {
    var seen = PendingChange.none(ACT).seen(T, at(5), DEBOUNCE);

    // The overlap of the next scans shows it again.
    assertThat(seen.seen(T, at(10), DEBOUNCE)).isEqualTo(seen);
    assertThat(seen.seen(T.minusSeconds(60), at(15), DEBOUNCE)).isEqualTo(seen);
  }

  @Test
  void aNewChangeDelaysTheActButNoLongerThanAnHourAfterTheFirst() {
    // A language every 8 minutes, each seen 5 minutes after its date.
    var change = PendingChange.none(ACT).seen(T, at(5), DEBOUNCE);
    for (var minute = 8; minute <= 64; minute += 8) {
      change = change.seen(at(minute), at(minute + 5), DEBOUNCE);
    }

    assertThat(change.firstSeen()).isEqualTo(at(5));
    assertThat(change.due()).isEqualTo(at(65));
  }

  @Test
  void aChangeThatTheLastReadHadIsNotReadAgain() {
    var read = PendingChange.none(ACT).seen(T, at(5), DEBOUNCE).read(at(25));

    assertThat(read.waiting()).isFalse();
    assertThat(read.readAt()).isEqualTo(at(25));
    // Seen again by the overlap: dated 20 minutes or more before the read, so it was in it.
    assertThat(read.seen(T, at(30), DEBOUNCE)).isEqualTo(read);
    // A change dated later may not have been shown to that read.
    var later = read.seen(at(10), at(30), DEBOUNCE);
    assertThat(later.waiting()).isTrue();
    assertThat(later.due()).isEqualTo(at(40));
  }

  @Test
  void aRecheckIsDueAtItsTimeUnlessAChangeIsDueSooner() {
    var recheck = PendingChange.none(ACT).recheck(at(45), at(15));

    assertThat(recheck.due()).isEqualTo(at(45));
    assertThat(PendingChange.none(ACT).seen(T, at(5), DEBOUNCE).recheck(at(45), at(15)).due())
        .isEqualTo(at(20));
    assertThat(recheck.recheck(at(40), at(16)).due()).isEqualTo(at(40));
  }

  /** Returns {@code minute} minutes after 15:00 on the day of the test. */
  private static Instant at(int minute) {
    return T.plus(Duration.ofMinutes(minute));
  }
}
