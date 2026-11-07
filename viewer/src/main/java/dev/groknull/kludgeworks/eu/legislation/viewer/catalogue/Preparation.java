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
 * An act being prepared in a language. It holds the state of the text of every version in that
 * language.
 *
 * <p>An act is prepared whole, up front. Thus its pages wait until it has settled. Then every text
 * is built or failed, and no text is queued or being built. A version that appears later is
 * prepared on its own, as an arriving version. The pages of the act do not wait for it.
 *
 * @param act the act
 * @param started when it was first opened in the language
 * @param settledAt when its last text was built or failed, or null while it is being prepared
 * @param texts the text of each of its versions in the language, in date order
 * @param steps what was done, in the order it was done
 * @param ahead about how many texts are ahead of its own texts in the queue, counting the texts of
 *     all acts. This applies while none of its texts is being built.
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

  /**
   * Returns whether it is being prepared, so that its pages wait. This is true if it has not
   * settled, the first time or again after Try again, and some text is not settled.
   */
  public boolean preparing() {
    return settledAt == null && !settled();
  }

  /**
   * Returns the versions that appeared after it had settled and whose texts are not yet settled, in
   * date order. Returns an empty list while it is being prepared.
   */
  public List<Version> arriving() {
    if (settledAt == null) {
      return List.of();
    }
    return texts.entrySet().stream()
        .filter(each -> !each.getValue().settled())
        .map(Map.Entry::getKey)
        .toList();
  }

  /** Returns whether every text is built or failed. */
  public boolean settled() {
    return texts.values().stream().allMatch(Text::settled);
  }

  /** Returns how long it took, once it is settled. */
  public Optional<Duration> took() {
    return Optional.ofNullable(settledAt).map(at -> Duration.between(started, at));
  }

  /** Returns how many texts are built. */
  public int ready() {
    return count(Text.Ready.class);
  }

  /** Returns how many texts failed. */
  public int failed() {
    return count(Text.Failed.class);
  }

  /**
   * Returns how many texts are being built now, that is, how many a worker has. It is 0 while the
   * act waits its turn, with its texts queued behind the texts of other acts.
   */
  public int running() {
    return count(Text.Building.class);
  }

  /** Returns how many versions are prepared. These are the versions in the language. */
  public int size() {
    return texts.size();
  }

  /** Returns the versions, in date order. */
  public List<Version> versions() {
    return List.copyOf(texts.sequencedKeySet());
  }

  private int count(Class<? extends Text> kind) {
    return (int) texts.values().stream().filter(kind::isInstance).count();
  }

  /**
   * A step of the preparation, as a reader is told it. The text of one version started, was built,
   * was queued again or failed.
   *
   * @param celex the version's document
   * @param attempt the attempt it was on
   * @param took how long its attempt took, once built. It is null if its start is not known.
   * @param failure why it failed, once failed
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
