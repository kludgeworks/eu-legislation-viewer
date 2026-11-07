// SPDX-License-Identifier: MIT
/**
 * Broadcast. The module sends the events that a module externalizes with {@code @Externalized} to
 * every instance through Valkey, as a {@link Broadcast}. Each instance uses them for what it holds
 * for its own readers, for example the pages that wait on the preparation of an act.
 *
 * <p>Every module shares this module, and it depends on none. An event is sent as JSON, and read
 * back as the type it was.
 *
 * <p>The API is the root package. Valkey's streams are the infrastructure, in {@code infra}.
 */
@ApplicationModule(displayName = "Broadcast")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
