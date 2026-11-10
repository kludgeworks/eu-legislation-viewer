// SPDX-License-Identifier: MIT
/**
 * Content holds the legal texts themselves.
 *
 * <ul>
 *   <li>A text is encoded as a Merkle tree. There is a node for each block and for each container.
 *       A block is, for example, a paragraph, a heading, a table row or a note. A container is, for
 *       example, a unit, a list or a table.
 *   <li>The SHA-256 of what a node holds is its hash. The hash of the root of the tree is the
 *       identity of the text.
 *   <li>Identifiers that move when a text is amended, the eIds, are not part of the hash of a node.
 *       They are in overlays on the edges to the nodes.
 *   <li>The tree is stored as chunks of about 16 KB. A chunk is a run of nodes, known by its hash.
 *       Content decides where the runs are cut. Thus a chunk that does not change between versions
 *       is stored once.
 *   <li>Nothing stored ever changes. A new version is a new root that reuses the chunks it can.
 * </ul>
 *
 * <p>The module is a sliced onion:
 *
 * <ul>
 *   <li>The root package is the API of the module and part of its domain ring. It holds {@link
 *       Content}, its hashes and its outlines. The {@code model} package holds the tree, its chunks
 *       and their rules.
 *   <li>The {@code application} package is the application ring. It stores and rebuilds texts, on a
 *       store of texts that it declares.
 *   <li>The {@code infra} package is the infrastructure: the store in Postgres.
 * </ul>
 */
@ApplicationModule(displayName = "Content")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
