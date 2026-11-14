// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;
import org.jspecify.annotations.Nullable;

/**
 * The text of a version in a language could not be converted, and another attempt will not convert
 * it.
 *
 * @param detail what went wrong, for a person to read. For example, the refusal of the converter or
 *     the last error.
 * @param trace the trace id of the attempt that failed. It is null if no trace was recorded.
 */
@DomainEvent
public record ConversionFailed(
    String celex, Language language, Failure failure, String detail, @Nullable String trace) {}
