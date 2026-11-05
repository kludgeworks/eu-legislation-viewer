// SPDX-License-Identifier: MIT
/**
 * Broadcast: the events a module externalizes (@Externalized), sent to every instance through
 * Valkey (Broadcast), for what each instance holds for its own readers, as the pages waiting on an
 * act's preparation. Shared by every module, depending on none: an event is sent as JSON, and read
 * back as the type it was.
 *
 * <p>The API is the root package; Valkey's streams are the infrastructure ({@code infra}).
 */
@ApplicationModule(displayName = "Broadcast")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
