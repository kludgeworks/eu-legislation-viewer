// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;
import org.jspecify.annotations.Nullable;

/**
 * An expression could not be built, and another attempt will not build it.
 *
 * @param detail what went wrong, for a person to read. For example, the refusal of the converter or
 *     the last error.
 * @param trace the trace id of the attempt that failed. It is null if no trace was recorded.
 */
@DomainEvent
public record ExpressionFailed(
    String celex, Language language, Failure failure, String detail, @Nullable String trace) {}
