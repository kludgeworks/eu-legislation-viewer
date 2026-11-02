// SPDX-License-Identifier: MIT
/**
 * Content: the legal texts themselves, stored as one Merkle tree that every version shares: a node
 * per block (a paragraph, a heading, a table row, a note) or container (a unit, a list, a table),
 * addressed by the SHA-256 of what it holds, so a node unchanged between versions is stored once.
 * Identifiers that move as a text is amended (eIds) are kept out of a node's hash, in overlays the
 * edges point to; a long run of blocks is cut into segments at boundaries its content decides, the
 * window a reader's page loads. Nothing stored ever changes: a version is a root, and a new version
 * a new root that reuses what it can.
 *
 * <p>A sliced onion: the root package is the module's API and part of its domain ring ({@link
 * Content}, its hashes and segments), with the tree and its rules ({@code model}); storing and
 * rebuilding texts is the application ring ({@code application}), on a store of trees it declares;
 * the store in Postgres is the infrastructure ({@code infra}).
 */
@ApplicationModule(displayName = "Content")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
