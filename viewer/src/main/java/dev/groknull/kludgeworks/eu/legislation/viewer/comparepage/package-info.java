// SPDX-License-Identifier: MIT
/**
 * The addresses of the compare page and of its parts, {@link ComparePage}.
 *
 * <p>The compare page is the catalogue's: it shows the act's masthead, timeline and states. Its
 * slots load the parts that the comparison module serves, by their addresses, with htmx. The rule
 * that ties a part to its page is the contract between the two modules: a part's address is the
 * page's address with a segment added ({@code changes}, {@code acts} or {@code same}), and the
 * page's query. The catalogue builds the addresses with this module, and the comparison module maps
 * its controller to its constants, so the rule is written once.
 *
 * <p>It knows nothing of either module's domain: an act is its CELEX, and a view is its code. All
 * of it is infrastructure, the outer ring of the onion.
 */
@ApplicationModule(displayName = "Compare page")
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparepage;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
