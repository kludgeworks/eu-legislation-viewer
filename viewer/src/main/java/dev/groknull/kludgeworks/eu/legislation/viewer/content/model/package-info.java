// SPDX-License-Identifier: MIT
/**
 * The content model. It holds a legal text as JSON, as it is stored. Each class makes one decision.
 *
 * <ul>
 *   <li>{@link LegalText} is what the model knows of the legal-text schema: its names, namespaces
 *       and whitespace.
 *   <li>{@link Encoder} writes a text as JSON, in the JsonML form, and {@link Decoder} reads the
 *       JSON back into {@code Element} records.
 *   <li>{@link Outlines} makes the outline of a text.
 * </ul>
 *
 * <p>These are pure functions on values. The application stores the JSON, in {@code TextStore}.
 */
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
