// SPDX-License-Identifier: MIT
/**
 * Access to Cellar, the repository of EU law of the Publications Office. It gives:
 *
 * <ul>
 *   <li>SPARQL queries over the RDF of Cellar, with {@link Sparql}.
 *   <li>Files read one at a time as they arrive, with {@link ContentStreams}.
 *   <li>The Formex files of one version, found from the RDF, with {@link FormexFiles}.
 * </ul>
 *
 * <p>This library applies the upstream etiquette for every caller:
 *
 * <ul>
 *   <li>It sends a descriptive User-Agent, set by the {@code viewer.cellar.*} properties in {@code
 *       application.yaml}.
 *   <li>It merges identical SPARQL queries that run at the same time into one query.
 *   <li>It refuses a result that reaches the row limit, because the result may be truncated.
 *   <li>It never fetches a whole zip. It fetches the entries of a zip-only version one by one.
 * </ul>
 *
 * <p>It does not throttle and does not retry. A page waits for the answer, so a failure shows at
 * once. The failure is a Spring {@code RestClientException} or a {@link CellarException}. {@link
 * CellarFailures} tells which failures mean that Cellar is not answering. Such a failure may pass.
 *
 * <p>The library holds no state and caches nothing.
 *
 * <p>It is a library, not a module of the viewer: it knows nothing of the viewer's domain. The
 * viewer registers its beans with {@link CellarConfiguration}, because its package is outside the
 * packages that the viewer scans.
 *
 * <p>All of it is infrastructure, the outer ring of the onion. Only the {@code infra} packages of
 * the viewer's modules may use it. Their domain must not use it.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.cellar;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
