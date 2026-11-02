// SPDX-License-Identifier: MIT
/**
 * The content model: a legal text as a tree of content-addressed nodes, one decision a class.
 *
 * <ul>
 *   <li>LegalText: what the model knows of legal-text's schema (its blocks, names, whitespace);
 *   <li>JsonMl: a block's XML as JSON, and back;
 *   <li>Annotations: the eIds kept out of content, as overlay facts;
 *   <li>NoteLinks: footnote references citing notes by their hashes;
 *   <li>Identity: a node's hash, of its canonical JSON;
 *   <li>Encoder and Decoder: the Tree these make, from a text and back;
 *   <li>Segmentation: a text's windows, an index on its tree.
 * </ul>
 *
 * <p>Pure functions on values: storing the tree is the application's (TreeStore).
 */
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
