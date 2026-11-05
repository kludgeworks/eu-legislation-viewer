// SPDX-License-Identifier: MIT
/**
 * The broadcast module's infrastructure: Valkey's streams, an externalized event added to its
 * stream by Modulith's event externalization (StreamTransport) and read back on every instance
 * (StreamReader), wired by BroadcastConfiguration.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.infra;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
