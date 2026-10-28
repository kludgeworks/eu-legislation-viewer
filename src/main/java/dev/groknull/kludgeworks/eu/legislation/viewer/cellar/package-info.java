// SPDX-License-Identifier: MIT
/**
 * Cellar, the Publications Office's repository of EU law, as the viewer reads it: SPARQL over its
 * RDF ({@link Sparql}), files fetched one at a time as raw bytes with conditional GETs ({@link
 * ContentStreams}), and the Formex files of one version found from the RDF ({@link FormexFiles}).
 *
 * <p>Upstream etiquette is enforced here, for every caller: a descriptive User-Agent, requests
 * serialised and spaced ({@code viewer.cellar.*}), retries with backoff on network errors, 5xx and
 * 429, identical concurrent SPARQL queries coalesced, and results that hit the row limit refused as
 * possibly truncated. Never a whole zip: a zip-only version's entries are fetched one by one.
 *
 * <p>The module holds no state and caches nothing; the modules that own the data cache it.
 */
@ApplicationModule(displayName = "Cellar")
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
