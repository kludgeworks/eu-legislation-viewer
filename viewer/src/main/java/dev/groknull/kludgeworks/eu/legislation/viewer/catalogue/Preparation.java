// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act being prepared in a language (Design: Preparing an act): every version's text in it, as it
 * stands. An act is prepared whole, up front, so its pages wait until it is settled: every text
 * built, or failed, none still being built.
 *
 * @param texts each version's text, in date order
 */
@ValueObject
public record Preparation(SequencedMap<Version, Text> texts) {

  public Preparation {
    texts = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(texts));
  }

  /** Whether no text is still being built. */
  public boolean settled() {
    return texts.values().stream().noneMatch(Text.Building.class::isInstance);
  }

  /** How many texts are built. */
  public int ready() {
    return (int) texts.values().stream().filter(Text.Ready.class::isInstance).count();
  }

  /**
   * How many texts are being built now: a worker has them. None while the act waits its turn, its
   * texts queued behind other acts'.
   */
  public int running() {
    return (int)
        texts.values().stream()
            .filter(
                text ->
                    text instanceof Text.Building building
                        && building.status().state() == IngestStatus.State.RUNNING)
            .count();
  }

  /** How many versions are prepared: those in the language. */
  public int size() {
    return texts.size();
  }

  /** The versions, in date order. */
  public List<Version> versions() {
    return List.copyOf(texts.sequencedKeySet());
  }
}
