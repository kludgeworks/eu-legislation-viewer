// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;
import org.jspecify.annotations.Nullable;

/**
 * An expression asked to be built: its job queued. The queue's entry, kept by Modulith's event
 * registry with the job, and handed to a worker once the job is committed (JobRunner); a worker
 * that stopped leaves it to be handed out again.
 *
 * @param traceparent the trace of the request that asked for it (W3C Trace Context), which its work
 *     continues
 */
@DomainEvent
public record ExpressionRequested(String celex, Language language, @Nullable String traceparent) {}
