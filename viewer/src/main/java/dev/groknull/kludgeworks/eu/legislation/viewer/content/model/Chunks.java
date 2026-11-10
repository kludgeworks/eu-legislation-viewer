// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A text as it is stored: its chunks, which {@link Chunker} makes and {@link Decoder} reads.
 *
 * <p>A chunk is a run of nodes, as JSON. Each chunk is known by the SHA-256 of its JSON. Thus a
 * chunk that two versions have in common is stored once.
 *
 * @param top the chunk that holds the root of the text
 * @param bodies the JSON of each chunk of the text, once, by its hash. The top chunk is first.
 */
public record Chunks(ContentHash top, Map<ContentHash, byte[]> bodies) {

  public Chunks {
    if (!bodies.containsKey(top)) {
      throw new IllegalArgumentException("The top chunk is missing: " + top.hex());
    }
    var ordered = new LinkedHashMap<ContentHash, byte[]>();
    ordered.put(top, bodies.get(top));
    ordered.putAll(bodies);
    bodies = Collections.unmodifiableMap(ordered);
  }

  /** Returns the hashes of the chunks, the top chunk first. */
  public List<ContentHash> manifest() {
    return List.copyOf(bodies.keySet());
  }

  /** Returns the JSON of a chunk of the text. */
  public byte[] body(ContentHash chunk) {
    return Objects.requireNonNull(bodies.get(chunk), chunk::hex);
  }
}
