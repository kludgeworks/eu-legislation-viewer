// SPDX-License-Identifier: MIT
/**
 * What the pages of the catalogue show, prepared for their templates. This package contains:
 *
 * <ul>
 *   <li>the timeline, in {@link TimelineView}
 *   <li>the wording that the pages use, in {@link Wording}
 *   <li>the links to the versions, in {@link Links}
 * </ul>
 *
 * <p>This package computes every link on a page. Thus the links are plain hrefs that work without
 * JavaScript.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
