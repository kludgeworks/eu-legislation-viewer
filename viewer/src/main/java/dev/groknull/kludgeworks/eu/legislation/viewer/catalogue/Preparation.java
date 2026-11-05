// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * An act being prepared in a language (Design: Preparing an act): every version's text in it, as it
 * stands. An act is prepared whole, up front, so its pages wait until it is settled: every text
 * built, or failed, none queued or being built.
 *
 * @param act the act
 * @param started when it was first opened in the language
 * @param settledAt when its last text was built or failed; none while it is being prepared
 * @param texts each of its versions' text in the language, in date order
 * @param steps what was done, in the order it was
 * @param ahead while none of its texts is being built, about how many texts are ahead of its own in
 *     the queue (every act's)
 */
@ValueObject
public record Preparation(
    ActIdentifier act,
    Language language,
    Instant started,
    @Nullable Instant settledAt,
    SequencedMap<Version, Text> texts,
    List<Step> steps,
    int ahead) {

  public Preparation {
    texts = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(texts));
    steps = List.copyOf(steps);
  }

  /** Whether every text is built or failed. */
  public boolean settled() {
    return texts.values().stream().allMatch(Text::settled);
  }

  /** How long it took, once it is settled. */
  public Optional<Duration> took() {
    return Optional.ofNullable(settledAt).map(at -> Duration.between(started, at));
  }

  /** How many texts are built. */
  public int ready() {
    return count(Text.Ready.class);
  }

  /** How many texts failed. */
  public int failed() {
    return count(Text.Failed.class);
  }

  /**
   * How many texts are being built now: a worker has them. None while the act waits its turn, its
   * texts queued behind other acts'.
   */
  public int running() {
    return count(Text.Building.class);
  }

  /** The versions whose text failed, in date order. */
  public List<Version> failedVersions() {
    return texts.entrySet().stream()
        .filter(each -> each.getValue() instanceof Text.Failed)
        .map(Map.Entry::getKey)
        .toList();
  }

  /** How many versions are prepared: those in the language. */
  public int size() {
    return texts.size();
  }

  /** The versions, in date order. */
  public List<Version> versions() {
    return List.copyOf(texts.sequencedKeySet());
  }

  private int count(Class<? extends Text> kind) {
    return (int) texts.values().stream().filter(kind::isInstance).count();
  }

  /**
   * A step of the preparation, as a reader is told it: one version's text started, built, queued
   * again or failed.
   *
   * @param celex the version's document
   * @param attempt the attempt it was on
   * @param took once built, how long its attempt took; none if its start isn't known
   * @param failure once failed, why
   */
  public record Step(
      Instant at,
      String celex,
      Kind kind,
      int attempt,
      @Nullable Duration took,
      @Nullable Failure failure) {

    public enum Kind {
      STARTED,
      BUILT,
      DEFERRED,
      FAILED
    }
  }
}
