// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * An attempt at a conversion failed, and a new attempt may succeed. For example, Cellar did not
 * answer, or the store was down. The conversion waits its turn again, to be tried a little later.
 *
 * @param attempt the attempt that failed, from 1
 * @param detail what went wrong, for a person
 */
@DomainEvent
public record ConversionPostponed(String celex, Language language, int attempt, String detail) {}
