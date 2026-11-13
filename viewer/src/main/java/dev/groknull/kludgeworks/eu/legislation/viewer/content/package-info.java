// SPDX-License-Identifier: MIT
/**
 * Content holds the legal texts themselves.
 *
 * <ul>
 *   <li>A text is the converter's output for one version (by its CELEX) in one language. Content
 *       keeps one text for each, known by a {@link TextId} that is never used again.
 *   <li>A text is kept whole, as the JSON of its element tree, compressed with zstd. Its eIds and
 *       note references are in it as the converter wrote them.
 *   <li>A text stored again with the same JSON writes nothing. With another JSON, it replaces the
 *       text before it, which is deleted, and {@link TextReplaced} tells the other modules. They
 *       delete what they keep by the old identity.
 *   <li>When Cellar withdraws a version, {@link VersionWithdrawn} deletes its texts in each
 *       language, and {@link TextRemoved} tells the other modules, which delete what they keep by
 *       each removed identity.
 *   <li>To read a text, Content reads its row and parses the JSON into {@link Element} records. A
 *       part of a text, for example a run of units, is cut from the whole text.
 * </ul>
 *
 * <p>The module is a sliced onion:
 *
 * <ul>
 *   <li>The root package is the API of the module and part of its domain ring. It holds {@link
 *       Content}, the identities, events and outlines of texts. The {@code model} package holds the
 *       JSON form of a text and its outline.
 *   <li>The {@code application} package is the application ring. It stores and reads texts, on a
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
