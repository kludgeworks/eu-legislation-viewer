// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Segment;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree;
import java.util.List;
import java.util.Optional;

/**
 * Where trees are kept: each node once, by its hash, and a text kept whole (a root kept means its
 * whole tree is), with its segments.
 */
public interface TreeStore {

  /** Whether the text at {@code root} is kept: if so, all of its tree is. */
  boolean contains(ContentHash root);

  /**
   * Keeps a text's tree and segments, writing only what isn't kept yet.
   *
   * @return how many of its nodes were new
   */
  int add(Tree tree, List<Segment> segments);

  /** The tree at {@code root}, if it is kept. */
  Optional<Tree> tree(ContentHash root);

  /** The segments of the text at {@code root}, in order. */
  List<Segment> segments(ContentHash root);
}
