// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Segment;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree;
import java.util.List;
import java.util.Optional;

/**
 * The store of trees. It keeps each node once, by its hash. It keeps a text whole, with its
 * segments. If a root is kept, its whole tree is kept.
 */
public interface TreeStore {

  /** Returns whether the text at {@code root} is kept. If it is, all of its tree is kept. */
  boolean contains(ContentHash root);

  /**
   * Keeps the tree and the segments of a text. It writes only what is not kept yet.
   *
   * @return how many of its nodes were new
   */
  int add(Tree tree, List<Segment> segments);

  /** Returns the tree at {@code root}, if it is kept. */
  Optional<Tree> tree(ContentHash root);

  /**
   * Returns the skeleton of the tree at {@code root}, if it is kept. The skeleton holds the
   * containers and the leaves that title them, such as num and heading. {@code Outlines} reads an
   * outline from it. The other children of the containers are not in it.
   */
  Optional<Tree> skeleton(ContentHash root);

  /** Returns the segments of the text at {@code root}, in order. */
  List<Segment> segments(ContentHash root);
}
