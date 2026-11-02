// SPDX-License-Identifier: MIT
/**
 * The content module's application ring: storing and rebuilding texts (ContentService), each a unit
 * of work, observed and logged, on a store of trees it declares (TreeStore) and the infrastructure
 * provides.
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
