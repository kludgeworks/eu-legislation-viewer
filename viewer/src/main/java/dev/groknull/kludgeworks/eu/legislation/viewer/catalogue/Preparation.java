// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act being prepared in a language (Design: Preparing an act): every version's text in it, as it
 * stands. An act is prepared whole, up front, so its pages wait until it is settled: every text
 * built, or failed, none queued or being built.
 *
 * @param act the act
 * @param texts each of its versions' text in the language, in date order
 */
@ValueObject
public record Preparation(ActIdentifier act, Language language, SequencedMap<Version, Text> texts) {

  public Preparation {
    texts = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(texts));
  }

  /** Whether every text is built or failed. */
  public boolean settled() {
    return texts.values().stream().allMatch(Text::settled);
  }

  /** How many texts are built. */
  public int ready() {
    return count(Text.Ready.class);
  }

  /**
   * How many texts are being built now: a worker has them. None while the act waits its turn, its
   * texts queued behind other acts'.
   */
  public int running() {
    return count(Text.Building.class);
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
}
