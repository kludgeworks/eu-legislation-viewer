// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Chunks;
import java.util.Optional;

/**
 * The store of texts. It keeps each chunk once, by its hash, and each text by its root: the list of
 * its chunks and its outline. If a root is kept, all of its chunks are kept.
 */
public interface TextStore {

  /** Returns whether the text at {@code root} is kept. */
  boolean contains(ContentHash root);

  /**
   * Keeps a text: the chunks that are not kept yet, then its list of chunks and its outline.
   *
   * @return how many of its chunks were new
   */
  int add(ContentHash root, Chunks chunks, Outline outline);

  /** Returns the chunks of the text at {@code root}, if it is kept. */
  Optional<Chunks> chunks(ContentHash root);

  /** Returns the outline of the text at {@code root}, if it is kept. */
  Optional<Outline> outline(ContentHash root);
}
