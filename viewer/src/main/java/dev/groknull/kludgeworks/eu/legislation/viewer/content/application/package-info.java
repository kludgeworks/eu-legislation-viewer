// SPDX-License-Identifier: MIT
/**
 * The application ring of the content module. {@link ContentService} stores, reads and outlines
 * texts. Each of these is a unit of work that is observed and logged. The service uses a store of
 * texts, {@link TextStore}, which this ring declares and the infrastructure provides.
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
