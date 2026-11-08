// SPDX-License-Identifier: MIT
/**
 * What the catalogue's pages show, prepared for their templates:
 *
 * <ul>
 *   <li>the home page and its search, in {@link HomeView}
 *   <li>the masthead of an act's page, in {@link MastheadView}, and its timeline, in {@link
 *       TimelineView}
 *   <li>the Reading card of the page of a version, in {@link ReadingView}, and the From and To
 *       cards of the compare page, in {@link CompareView}
 *   <li>the preparing page, {@link PreparingView}, the failure panel, {@link FailureView}, and the
 *       versions that are arriving, {@link ArrivingView}
 *   <li>the wording that the pages use, in {@link Wording}, and the links to every page, in {@link
 *       Links}, so the links are plain hrefs that work without JavaScript
 * </ul>
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
