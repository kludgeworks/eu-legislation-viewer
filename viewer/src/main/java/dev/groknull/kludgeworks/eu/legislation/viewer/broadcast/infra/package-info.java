// SPDX-License-Identifier: MIT
/**
 * The infrastructure of the broadcast module, on Valkey's streams. {@link StreamTransport} adds an
 * externalized event to its stream, for Modulith's event externalization. {@link StreamReader}
 * reads it back on every instance. {@link BroadcastConfiguration} wires them.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.infra;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
