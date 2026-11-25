// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import org.jspecify.annotations.Nullable;

/**
 * The version date of a change: the first day of the first version whose text newly tags the
 * changed place with the amending act. {@link TheTextAndTheLawOnADay#versionDateOfAChange} gives
 * it.
 *
 * @param version the version whose first day is the version date, or null if no version includes
 *     the amending act
 */
record VersionDate(@Nullable Version version) {}
