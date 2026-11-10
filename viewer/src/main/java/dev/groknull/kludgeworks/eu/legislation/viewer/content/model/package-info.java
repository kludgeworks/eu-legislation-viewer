// SPDX-License-Identifier: MIT
/**
 * The content model. It holds a legal text as a tree of content-addressed nodes. Each class makes
 * one decision.
 *
 * <ul>
 *   <li>{@link LegalText} is what the model knows of the legal-text schema: its blocks, names and
 *       whitespace.
 *   <li>{@link JsonMl} maps the XML of a block to JSON, and back.
 *   <li>{@link Annotations} keeps the eIds out of the content, as overlay facts.
 *   <li>{@link NoteLinks} makes footnote references cite notes by their hashes.
 *   <li>{@link Identity} makes the hash of a node, from its canonical JSON.
 *   <li>{@link Encoder} makes the {@link Tree} from a text.
 *   <li>{@link Chunker} cuts the tree into {@link Chunks}, as it is stored, and {@link Decoder}
 *       makes the text back from them.
 *   <li>{@link Outlines} makes the outline of a text from its tree.
 * </ul>
 *
 * <p>These are pure functions on values. The application stores the chunks, in {@code TextStore}.
 */
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
