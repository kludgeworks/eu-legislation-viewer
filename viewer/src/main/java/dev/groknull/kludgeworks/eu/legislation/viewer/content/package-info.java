// SPDX-License-Identifier: MIT
/**
 * Content holds the legal texts themselves. It stores them as one Merkle tree that every version
 * shares.
 *
 * <ul>
 *   <li>There is a node for each block and for each container. A block is, for example, a
 *       paragraph, a heading, a table row or a note. A container is, for example, a unit, a list or
 *       a table.
 *   <li>The SHA-256 of what a node holds is its address. Thus a node that does not change between
 *       versions is stored once.
 *   <li>Identifiers that move when a text is amended, the eIds, are not part of the hash of a node.
 *       They are in overlays that the edges point to.
 *   <li>A long run of blocks is cut into segments, at boundaries that its content decides. A
 *       segment is the window that the page of a reader loads.
 *   <li>Nothing stored ever changes. A version is a root. A new version is a new root that reuses
 *       what it can.
 * </ul>
 *
 * <p>The module is a sliced onion:
 *
 * <ul>
 *   <li>The root package is the API of the module and part of its domain ring. It holds {@link
 *       Content}, its hashes and its segments. The {@code model} package holds the tree and its
 *       rules.
 *   <li>The {@code application} package is the application ring. It stores and rebuilds texts, on a
 *       store of trees that it declares.
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
