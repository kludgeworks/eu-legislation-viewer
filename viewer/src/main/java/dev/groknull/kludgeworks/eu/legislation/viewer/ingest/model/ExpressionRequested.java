// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;
import org.jspecify.annotations.Nullable;

/**
 * A request to build an expression. Its job is queued.
 *
 * <p>This event is the entry in the queue. The event registry of Modulith keeps it with the job.
 * The registry gives it to a worker in {@code JobRunner} when the job is committed. If a worker
 * stops, the registry gives the event out again.
 *
 * @param traceparent the trace of the request that asked for the build, in W3C Trace Context form.
 *     The work continues this trace.
 */
@DomainEvent
public record ExpressionRequested(String celex, Language language, @Nullable String traceparent) {}
