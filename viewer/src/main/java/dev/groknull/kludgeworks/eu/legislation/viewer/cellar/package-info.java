// SPDX-License-Identifier: MIT
/**
 * Cellar, the Publications Office's repository of EU law, as the viewer reads it: SPARQL over its
 * RDF ({@link Sparql}), files fetched one at a time as raw bytes with conditional GETs ({@link
 * ContentStreams}), and the Formex files of one version found from the RDF ({@link FormexFiles}).
 *
 * <p>Upstream etiquette is enforced here, for every caller: a descriptive User-Agent ({@code
 * viewer.cellar.*}, in application.yaml), identical concurrent SPARQL queries coalesced, and
 * results that hit the row limit refused as possibly truncated. No throttling and no retries: a
 * page waits on the answer, so a failure surfaces at once, as Spring's {@code RestClientException}
 * or a {@link CellarException}. Never a whole zip: a zip-only version's entries are fetched one by
 * one.
 *
 * <p>The module holds no state and caches nothing; the modules that own the data cache it.
 *
 * <p>All of it is infrastructure (the onion's outer ring): only other modules' {@code infra}
 * packages may use it, never their domain; and, as an adapter the app drives (jMolecules' secondary
 * adapter), never their {@code web}.
 */
@ApplicationModule(displayName = "Cellar")
@InfrastructureRing
@SecondaryAdapter
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
