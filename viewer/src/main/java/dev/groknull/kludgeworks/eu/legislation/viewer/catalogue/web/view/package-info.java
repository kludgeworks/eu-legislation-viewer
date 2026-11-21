// SPDX-License-Identifier: MIT
/**
 * What the catalogue's pages show, prepared for their templates:
 *
 * <ul>
 *   <li>the home page and its search, in {@link HomeView}
 *   <li>the masthead of an act's page, in {@link MastheadView}
 *   <li>the Reading card of the page of a version, in {@link ReadingView}, and the head of the
 *       compare page, in {@link CompareView}
 *   <li>the preparing page, {@link PreparingView}, the failure panel, {@link FailureView}, and the
 *       versions that are arriving, {@link ArrivingView}
 *   <li>an act's page while the viewer looks the act up, {@link ActLookupView}
 *   <li>the wording that the pages use, in {@link Wording}, and the links to every page, in {@link
 *       Links}, so the links are plain hrefs that work without JavaScript
 * </ul>
 *
 * <p>A view only formats. Each decision about the law or its dates, such as from when an act
 * applies or which version applies on a day, is a method of a domain class with its own test. A
 * view reads the kind of a date or whether it is for a part only to print it.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
