// SPDX-License-Identifier: MIT
/**
 * The EU's official languages, as the Publications Office publishes them in its language authority
 * table. {@link Language} is the shared kernel of the viewer: every module uses it, and no module
 * owns it. The catalogue lists the languages of an act, ingest fetches a text in a language, and
 * content keeps one text for each version and language. Thus modules that call each other, as the
 * catalogue calls ingest, do not depend on each other for it.
 *
 * <p>The kernel is small on purpose. It holds the EU's model of its languages and nothing of the
 * viewer's own. A concept enters it only if every module needs it and no module owns it.
 */
@ApplicationModule(displayName = "Language")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.language;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
